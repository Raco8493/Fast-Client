import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parent.parent
os.environ['PHONECRAFT_HOME'] = tempfile.mkdtemp(prefix='phonecraft-test-')
import sys
sys.path.insert(0, str(ROOT))
import phonecraft as pc


class ConfigurationTests(unittest.TestCase):
    def setUp(self):
        pc.HOST.config = pc.DEFAULTS.copy()
        pc.HOST.process = None
        pc.HOST.installing = False

    def test_mode_change(self):
        pc.HOST.set_config({'mode': 'fabric', 'ram_percent': 85})
        self.assertEqual(pc.HOST.config['mode'], 'fabric')
        self.assertEqual(json.loads(pc.CONFIG_FILE.read_text())['ram_percent'], 85)

    def test_bad_resource_settings(self):
        for invalid in [{'ram_percent': 95}, {'cpu_percent': 101}, {'mode': 'vanilla'}, {'ram_percent': True}]:
            with self.assertRaises(ValueError):
                pc.HOST.set_config(invalid)

    def test_safe_filenames(self):
        self.assertTrue(pc.is_safe_filename('Geyser-Fabric.jar'))
        self.assertFalse(pc.is_safe_filename('../evil.jar'))
        self.assertFalse(pc.is_safe_filename('server.zip'))
        self.assertFalse(pc.is_safe_filename('.hidden.jar'))

    def test_heap_never_uses_all_android_ram(self):
        mb = 1024
        memory = {'MemTotal': 8 * mb * mb, 'MemAvailable': 6 * mb * mb}
        heap = pc.java_heap_mb(85, memory)
        self.assertLessEqual(heap, 6 * mb - 750)
        self.assertLessEqual(heap, int(8 * mb * 0.85))

    def test_heap_rejects_low_available_memory(self):
        with self.assertRaises(RuntimeError):
            pc.java_heap_mb(85, {'MemTotal': 8 * 1024 * 1024, 'MemAvailable': 900 * 1024})

    def test_eula_is_explicit(self):
        with self.assertRaises(ValueError):
            pc.HOST.install_async(False)

    def test_upload_paper_plugin(self):
        root = pc.profile_dir('paper')
        root.mkdir(parents=True, exist_ok=True)
        (root / 'server.jar').write_bytes(b'dummy')
        pc.HOST.set_config({'mode': 'paper'})
        pc.HOST.upload('test.jar', b'xyz' * 300)
        self.assertEqual((root / 'plugins' / 'test.jar').read_bytes(), b'xyz' * 300)

    def test_properties_keep_other_values(self):
        with tempfile.TemporaryDirectory() as d:
            f = Path(d) / 'server.properties'
            f.write_text('seed=1\nmax-players=3\n')
            pc.set_property(f, 'max-players', 12)
            self.assertIn('seed=1', f.read_text())
            self.assertIn('max-players=12', f.read_text())
            self.assertEqual(f.read_text().count('max-players='), 1)

    def test_console_requires_running_server(self):
        with self.assertRaises(RuntimeError):
            pc.HOST.command('say hi')


if __name__ == '__main__':
    unittest.main()
