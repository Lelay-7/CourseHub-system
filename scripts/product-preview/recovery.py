"""Guarded backup validation and restore into an empty, owned demo MySQL instance."""
import hashlib
import os
from pathlib import Path
import re
import stat
import subprocess
import zipfile

import manage


def fingerprint(config):
    manage.identity(config)
    tables = manage.mysql(config, f"SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='{config['database']}' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME;").splitlines()
    result = {}
    for table in tables:
        if not re.fullmatch(r'[a-z_]+', table): raise RuntimeError('Unexpected table name')
        columns = manage.mysql(config, f"SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='{config['database']}' AND TABLE_NAME='{table}' ORDER BY ORDINAL_POSITION;").splitlines()
        order = ','.join('`' + value + '`' for value in columns)
        rows = manage.mysql(config, f"SELECT * FROM `{config['database']}`.`{table}` ORDER BY {order};")
        result[table] = {'rows': int(manage.mysql(config, f"SELECT COUNT(*) FROM `{config['database']}`.`{table}`;")),
                         'sha256': hashlib.sha256(rows.encode('utf-8')).hexdigest()}
    return result


def validate_backup(directory):
    directory = Path(directory).resolve()
    manage.no_reparse(directory)
    manifest = manage.load(directory / 'manifest.json')
    if manifest['status'] != 'complete': raise RuntimeError('Incomplete backup')
    for name, info in manifest['files'].items():
        if Path(name).name != name: raise RuntimeError('Unexpected backup member')
        path = directory / name
        with path.open('rb') as stream: digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        if digest != info['sha256'] or path.stat().st_size != info['bytes']: raise RuntimeError('Backup checksum mismatch: ' + name)
    with zipfile.ZipFile(directory / 'runtime.zip') as archive:
        if archive.testzip() is not None: raise RuntimeError('Damaged runtime archive')
        config = __import__('json').loads(archive.read('config.json').decode('utf-8'))
    if manifest['run_id'] != config['run_id'] or config['database'] != 'coursehub_product_preview_' + config['run_id'] or not re.fullmatch(r'[a-f0-9]{32}', config['nonce']):
        raise RuntimeError('Backup identity mismatch')
    sql = (directory / 'demo.sql').read_text(encoding='utf-8-sig')
    targets = re.findall(r'(?im)^\s*(?:CREATE DATABASE(?:\s*/\*.*?\*/)?(?:\s+IF NOT EXISTS)?(?:\s*/\*.*?\*/)?|USE|DROP DATABASE(?: IF EXISTS)?)\s+`?([a-zA-Z0-9_]+)', sql)
    if not targets or set(targets) != {config['database']}: raise RuntimeError('Dump targets an unexpected database')
    return manifest, config, sql


def extract_runtime(directory, destination, overwrite=False):
    destination = Path(destination).resolve()
    manage.no_reparse(destination)
    with zipfile.ZipFile(Path(directory) / 'runtime.zip') as archive:
        for info in archive.infolist():
            if info.is_dir(): continue
            if stat.S_ISLNK(info.external_attr >> 16): raise RuntimeError('Archive contains a symlink')
            if not (info.filename in ('config.json', 'mailbox.json', 'backend-runtime.jar') or info.filename.startswith(('uploads/', 'frontend-dist/'))):
                raise RuntimeError('Unexpected archive path')
            target = (destination / info.filename).resolve()
            if not target.is_relative_to(destination): raise RuntimeError('Archive escapes destination')
            if target.exists() and not overwrite: raise RuntimeError('Refusing to overwrite runtime data')
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(info))


def mysql_program(config):
    root = manage.require_path(config)
    return manage.start_program([str(manage.MYSQL_HOME / 'bin/mysqld.exe'), '--no-defaults',
        '--basedir=' + manage.MYSQL_HOME.as_posix(), '--datadir=' + (root / 'data').as_posix(),
        '--pid-file=' + (root / 'mysql.pid').as_posix(), '--bind-address=127.0.0.1', f"--port={config['mysql_port']}",
        '--mysqlx=0', '--skip-log-bin', '--innodb-buffer-pool-size=64M', '--console'], manage.HERE, 'recovery-mysql')


def stop_mysql(config, program):
    process, owned, log = program
    try:
        if owned.alive():
            manage.identity(config)
            manage.mysql(config, 'SHUTDOWN;')
            process.wait(timeout=20)
    finally:
        owned.close(kill=True)
        log.close()


def restore_empty(config, sql, overlay=None):
    root = manage.require_path(config)
    data = root / 'data'
    if any(data.iterdir()): raise RuntimeError('Restore destination is not empty')
    for port in (config['mysql_port'],):
        with __import__('socket').socket() as probe: probe.bind(('127.0.0.1', port))
    with (manage.HERE / 'restore-initialize.log').open('wb') as log:
        subprocess.run([str(manage.MYSQL_HOME / 'bin/mysqld.exe'), '--no-defaults', '--basedir=' + manage.MYSQL_HOME.as_posix(),
            '--datadir=' + data.as_posix(), '--initialize-insecure', '--console'], stdout=log, stderr=log, check=True,
            timeout=75, creationflags=manage.NO_WINDOW)
    program = mysql_program(config)
    password_set = False
    try:
        manage.wait_for(lambda: manage.mysql(config, 'SELECT 1;', password=False) == '1', 45, 'Restore MySQL')
        manage.identity(config, password=False)
        manage.mysql(config, "ALTER USER 'root'@'localhost' IDENTIFIED BY '" + config['root_password'] + "';", password=False)
        password_set = True
        manage.mysql(config, sql)
        if manage.mysql(config, f"SELECT nonce FROM `{config['database']}`.local_preview_guard;") != config['nonce']:
            raise RuntimeError('Restored ownership nonce does not match')
        if overlay:
            ids = set(manage.mysql(config, f"SELECT user_id FROM `{config['database']}`.user;").splitlines())
            if not ids.issubset(set(overlay['user_ids'])): raise RuntimeError('Recovered users would remove backup accounts')
            manage.mysql(config, f"USE `{config['database']}`; SET FOREIGN_KEY_CHECKS=0; DELETE FROM user; DELETE FROM chat_send_rate_window;\n" + overlay['sql'] + '\nSET FOREIGN_KEY_CHECKS=1;')
        manage.mysql(config, "CREATE USER 'product_preview'@'localhost' IDENTIFIED BY '" + config['db_password'] + "'; GRANT ALL PRIVILEGES ON `" + config['database'] + "`.* TO 'product_preview'@'localhost';")
        return fingerprint(config)
    finally:
        if password_set: stop_mysql(config, program)
        else:
            program[1].close(kill=True); program[2].close()
