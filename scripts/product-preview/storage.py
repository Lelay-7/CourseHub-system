"""Shared, durable demo storage for all worktrees of this repository."""
import os
import hashlib
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
common = subprocess.check_output(['git', 'rev-parse', '--git-common-dir'], cwd=ROOT, text=True, encoding='utf-8').strip()
REPOSITORY_KEY = hashlib.sha256(str((ROOT / common).resolve()).lower().encode('utf-8')).hexdigest()[:12]
# MySQL on this Windows installation fails to consume non-ASCII datadir arguments.
# ProgramData is persistent and ASCII; the common Git identity shares it across worktrees.
DATA_HOME = Path(os.environ.get('PROGRAMDATA', r'C:\ProgramData')) / 'CourseHubLocalDemo' / REPOSITORY_KEY


def runtime_directory():
    override = os.environ.get('COURSEHUB_DEMO_RUNTIME')
    if not override:
        return DATA_HOME / 'runtime'
    path = Path(override).resolve()
    relative = path.relative_to(DATA_HOME.resolve()).parts
    if len(relative) != 3 or relative[0] != 'rehearsals' or not re.fullmatch(r'[a-f0-9]{32}', relative[1]) or relative[2] != 'runtime':
        raise RuntimeError('Unexpected rehearsal runtime path')
    return path


def data_directory(run_id, runtime=None):
    if not re.fullmatch(r'[a-f0-9]{32}', run_id):
        raise RuntimeError('Unexpected demo run identity')
    runtime = runtime or runtime_directory()
    parent = DATA_HOME if runtime == DATA_HOME / 'runtime' else runtime.parent
    return parent / 'mysql' / ('coursehub-product-preview-' + run_id)


def no_reparse(path):
    path = Path(path)
    while True:
        if path.is_symlink() or path.is_junction():
            raise RuntimeError('Demo storage must not contain reparse points')
        if path == path.parent:
            break
        path = path.parent
