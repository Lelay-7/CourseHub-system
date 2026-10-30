"""Preserve legacy demo bytes and activate a copied database in durable storage."""
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
import shutil
import socket

import manage


def inventory(root):
    result = {}
    for path in sorted(root.rglob('*')):
        manage.no_reparse(path)
        if path.is_file():
            with path.open('rb') as stream:
                result[path.relative_to(root).as_posix()] = {'bytes': path.stat().st_size, 'sha256': hashlib.file_digest(stream, 'sha256').hexdigest()}
    return result


def migrate():
    legacy = manage.ROOT / 'backend/target/product-preview'
    config = manage.load(legacy / 'config.json')
    run_id = config['run_id']
    if not re.fullmatch(r'[a-f0-9]{32}', run_id) or config['database'] != 'coursehub_product_preview_' + run_id:
        raise RuntimeError('Unexpected legacy demo identity')
    source = Path(r'C:\Windows\Temp') / ('coursehub-product-preview-' + run_id)
    if Path(config['data_root']) != source or not re.fullmatch(r'[a-f0-9]{32}', config['nonce']):
        raise RuntimeError('Legacy configuration does not match expected demo path')
    manage.no_reparse(source)
    manage.no_reparse(legacy)
    if manage.CONFIG.exists():
        raise RuntimeError('Durable runtime already exists; inspect it rather than overwrite')
    state = manage.load(legacy / 'state.json')
    if manage.supervisor_running(state):
        raise RuntimeError('Stop the legacy owned supervisor before migration')
    for key in ('web_port', 'api_port', 'mysql_port', 'mailbox_port', 'smtp_port'):
        with socket.socket() as probe:
            probe.bind(('127.0.0.1', config[key]))
    if not (source / 'data/mysql.ibd').is_file():
        raise RuntimeError('Existing MySQL system tablespace is missing; use an isolated backup restoration instead')
    preservation = manage.DATA_HOME / 'preservation' / (datetime.now().strftime('%Y%m%d-%H%M%S') + '-legacy')
    preservation.mkdir(parents=True, exist_ok=False)
    original = inventory(source)
    shutil.copytree(source, preservation / 'legacy-mysql')
    shutil.copytree(legacy, preservation / 'legacy-runtime')
    if inventory(preservation / 'legacy-mysql') != original or inventory(source) != original:
        raise RuntimeError('Preservation verification failed; original data has not been changed')
    target = manage.data_directory(run_id, manage.HERE)
    manage.no_reparse(target)
    target.mkdir(parents=True, exist_ok=False)
    shutil.copytree(preservation / 'legacy-mysql/data', target / 'data')
    if inventory(target / 'data') != inventory(source / 'data'):
        raise RuntimeError('Database copy differs before recovery')
    (target / '.coursehub-run-marker').write_text(config['nonce'], encoding='utf-8')
    # The temp cleanup removed small system-schema files/directories; InnoDB tables
    # remain in mysql.ibd. Only repair directory containers in the working copy.
    for name in ('mysql', 'performance_schema', 'sys'):
        (target / 'data' / name).mkdir(exist_ok=True)
    for name in ('uploads',):
        shutil.copytree(preservation / 'legacy-runtime' / name, manage.HERE / name)
    for name in ('mailbox.json', 'backend-runtime.jar'):
        shutil.copy2(preservation / 'legacy-runtime' / name, manage.HERE / name)
    config['data_root'] = str(target)
    manage.save(manage.CONFIG, config)
    manage.save(manage.STATE, {'status': 'stopped', 'run_id': run_id})
    proof = {'status': 'copied', 'run_id': run_id, 'source': str(source), 'target': str(target),
             'preservation': str(preservation), 'original_unchanged': inventory(source) == original,
             'original_marker_present': (source / '.coursehub-run-marker').exists(),
             'files': original}
    manage.save(preservation / 'migration.json', proof)
    manage.save(manage.DATA_HOME / 'migration-latest.json', {key: value for key, value in proof.items() if key != 'files'})
    print(json.dumps({key: value for key, value in proof.items() if key != 'files'}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    migrate()
