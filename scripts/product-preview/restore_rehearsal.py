"""Restore a backup in a separate local instance, verify it, and stop only that instance."""
import argparse
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import uuid
import hashlib
import urllib.request

import manage
import recovery


def check_files():
    config = manage.load(manage.CONFIG)
    manage.identity(config)
    keys = manage.mysql(config, f"SELECT storage_key FROM `{config['database']}`.material WHERE storage_key IS NOT NULL UNION SELECT storage_key FROM `{config['database']}`.material_revision WHERE storage_key IS NOT NULL;").splitlines()
    uploads = (manage.HERE / 'uploads').resolve()
    for key in keys:
        path = (uploads / key).resolve()
        if not path.is_relative_to(uploads) or not path.is_file(): raise RuntimeError('Missing or invalid restored file reference')
    selected = manage.mysql(config, f"SELECT material_id,storage_key FROM `{config['database']}`.material WHERE status=1 AND is_deleted=0 AND download_score=0 AND storage_key IS NOT NULL ORDER BY material_id LIMIT 1;").split('\t')
    if len(selected) != 2: raise RuntimeError('No free test material to verify download')
    account = next(account for account in config['accounts'] if account['username'] == 'product_student_a')
    login = manage.api(config, '/api/auth/login', {'username': account['username'], 'password': account['password']})
    request = urllib.request.Request(f"http://localhost:{config['web_port']}/api/material/{selected[0]}/download", headers={'Authorization': 'Bearer ' + login['accessToken']})
    with urllib.request.urlopen(request, timeout=15) as response: actual = response.read()
    expected = (uploads / selected[1]).read_bytes()
    if actual != expected: raise RuntimeError('Downloaded bytes differ from restored file')
    proof = {'status': 'pass', 'referenced_files_present': len(keys), 'downloaded_material_id': selected[0],
             'download_sha256': hashlib.sha256(actual).hexdigest()}
    manage.save(manage.HERE / 'file-verification.json', proof)
    print(json.dumps(proof), flush=True)


def worker(backup):
    manifest, archived, sql = recovery.validate_backup(backup)
    config = manage.load(manage.CONFIG)
    if not config.get('restore_rehearsal') or manage.HERE == manage.DATA_HOME / 'runtime':
        raise RuntimeError('Restore worker requires an isolated rehearsal runtime')
    if config['run_id'] != archived['run_id'] or config['nonce'] != archived['nonce']:
        raise RuntimeError('Rehearsal configuration differs from its backup')
    print('Initializing isolated backup database...', flush=True)
    actual = recovery.restore_empty(config, sql)
    expected = manage.load(backup / 'database-fingerprint.json')
    if actual != expected: raise RuntimeError('Restored table rows or hashes differ from backup')
    manage.save(manage.HERE / 'database-verification.json', {'status': 'pass', 'tables': actual})
    print(f'Restored rows and hashes match across {len(actual)} tables.', flush=True)


def rehearse(backup):
    manifest, config, _ = recovery.validate_backup(backup)
    if not (backup / 'database-fingerprint.json').is_file():
        raise RuntimeError('Create a new fingerprinted backup before rehearsal')
    session = uuid.uuid4().hex
    root = manage.DATA_HOME / 'rehearsals' / session
    root.mkdir(parents=True, exist_ok=False)
    runtime = root / 'runtime'
    runtime.mkdir()
    recovery.extract_runtime(backup, runtime)
    data_root = manage.data_directory(config['run_id'], runtime)
    (data_root / 'data').mkdir(parents=True, exist_ok=False)
    (data_root / '.coursehub-run-marker').write_text(config['nonce'], encoding='utf-8')
    sockets = []
    try:
        for key in ('web_port', 'api_port', 'mysql_port', 'mailbox_port', 'smtp_port'):
            probe = socket.socket(); probe.bind(('127.0.0.1', 0)); sockets.append(probe)
            config[key] = probe.getsockname()[1]
        config.update(data_root=str(data_root), restore_rehearsal=True,
                      git_commit=manifest['commit'],
                      web_url=f"http://localhost:{config['web_port']}")
        manage.save(runtime / 'config.json', config)
        manage.save(runtime / 'state.json', {'status': 'stopped', 'run_id': config['run_id']})
    finally:
        for probe in sockets: probe.close()
    env = {**os.environ, 'COURSEHUB_DEMO_RUNTIME': str(runtime)}
    proof = {'status': 'failed', 'backup': str(backup), 'commit': manifest['commit'], 'runtime': str(runtime),
             'ports': {key: config[key] for key in ('web_port', 'api_port', 'mysql_port', 'mailbox_port', 'smtp_port')}}
    try:
        subprocess.run([sys.executable, __file__, '--worker', str(backup)], env=env, check=True)
        subprocess.run([sys.executable, str(Path(__file__).with_name('local_demo.py')), 'start', '--no-browser'], env=env, check=True)
        # Reuse the real form-login check; it discovers the isolated runtime via the environment.
        env['PLAYWRIGHT_BROWSERS_PATH'] = str(Path.home() / 'AppData/Local/ms-playwright')
        subprocess.run([str(manage.NODE), 'scripts/product-preview-login-browser.mjs'], cwd=manage.ROOT / 'frontend', env=env, check=True)
        subprocess.run([sys.executable, __file__, '--check-files'], env=env, check=True)
        proof.update(status='pass', database_rows_and_hashes=True, archived_frontend_and_backend=True,
                     four_roles_real_form_login=True, file_archive_checksum_verified=True,
                     real_download_bytes_match=True,
                     table_count=len(manage.load(runtime / 'database-verification.json')['tables']))
    finally:
        stopped = subprocess.run([sys.executable, str(Path(__file__).with_name('local_demo.py')), 'stop'], env=env, capture_output=True, text=True, encoding='utf-8')
        state = manage.load(runtime / 'state.json')
        proof['stopped'] = stopped.returncode == 0 and state['status'] == 'stopped'
        proof['ports_closed'] = True
        for port in proof['ports'].values():
            with socket.socket() as probe:
                probe.settimeout(.5)
                if probe.connect_ex(('127.0.0.1', port)) == 0: proof['ports_closed'] = False
        if not proof['stopped'] or not proof['ports_closed']: proof['status'] = 'failed'
        manage.save(root / 'result.json', proof)
        manage.save(manage.DATA_HOME / 'rehearsal-latest.json', {'status': proof['status'], 'path': str(root / 'result.json')})
    if proof['status'] != 'pass': raise RuntimeError('Rehearsal failed; inspect ' + str(root / 'result.json'))
    print(json.dumps(proof, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--backup', default='latest')
    parser.add_argument('--worker', type=Path)
    parser.add_argument('--check-files', action='store_true')
    args = parser.parse_args()
    if args.check_files: check_files()
    elif args.worker: worker(args.worker.resolve())
    else:
        selected = Path(manage.load(manage.DATA_HOME / 'backups/latest.json')['path']) if args.backup == 'latest' else Path(args.backup)
        rehearse(selected.resolve())
