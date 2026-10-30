import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import manage
import storage


class StorageTests(unittest.TestCase):
    def test_shared_home_does_not_depend_on_build_or_temp(self):
        self.assertEqual(storage.DATA_HOME.parent.name, 'CourseHubLocalDemo')
        self.assertTrue(str(storage.DATA_HOME).isascii())
        self.assertNotIn('target', storage.DATA_HOME.parts)
        self.assertNotIn('Temp', storage.DATA_HOME.parts)

    def test_only_valid_run_ids_can_select_a_database_directory(self):
        for value in ('../other', '', 'a' * 31, 'z' * 32):
            with self.assertRaises(RuntimeError): storage.data_directory(value)

    def test_unknown_runtime_override_is_refused(self):
        with patch.dict(os.environ, {'COURSEHUB_DEMO_RUNTIME': str(storage.DATA_HOME / 'anything')}):
            with self.assertRaises(RuntimeError): storage.runtime_directory()

    def test_rehearsal_uses_separate_root(self):
        target = storage.DATA_HOME / 'rehearsals' / ('b' * 32) / 'runtime'
        with patch.dict(os.environ, {'COURSEHUB_DEMO_RUNTIME': str(target)}):
            self.assertEqual(storage.runtime_directory(), target)
            self.assertNotEqual(storage.data_directory('a' * 32, target), storage.data_directory('a' * 32, storage.DATA_HOME / 'runtime'))

    def test_data_path_and_marker_and_database_are_all_required(self):
        test_home = manage.ROOT / 'backend/target/storage-test'
        test_home.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=test_home) as folder:
            root = Path(folder)
            self.assertTrue(root.resolve().is_relative_to(test_home.resolve()))
            (root / 'data').mkdir()
            config = {'run_id': 'a' * 32, 'nonce': 'b' * 32, 'data_root': str(root), 'database': 'coursehub_product_preview_' + 'a' * 32}
            with patch.object(manage, 'data_directory', return_value=root):
                with self.assertRaises(FileNotFoundError): manage.require_path(config)
                (root / '.coursehub-run-marker').write_text('wrong', encoding='utf-8')
                with self.assertRaises(RuntimeError): manage.require_path(config)
                (root / '.coursehub-run-marker').write_text(config['nonce'], encoding='utf-8')
                self.assertEqual(manage.require_path(config), root)
                with self.assertRaises(RuntimeError): manage.require_path({**config, 'data_root': str(root.parent)})
                with self.assertRaises(RuntimeError): manage.require_path({**config, 'database': 'business_db'})


if __name__ == '__main__': unittest.main()
