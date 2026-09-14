import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("release", Path(__file__).resolve().parents[1] / "release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseTests(unittest.TestCase):
    def fixture(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        root = Path(temp.name)
        files = ['libs/krc-villagers-1.0.0.jar', 'libs/krc-villagers-1.0.0-sources.jar', 'distributions/krc-villagers-1.0.0-project.zip']
        lines = []
        for name in files:
            target = root / 'build' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(name.encode())
            lines.append(f'{release.digest(target)}  {name}\n')
        (root / 'build/SHA256SUMS.txt').write_text(''.join(lines))
        return root

    def test_release_copies_exact_build_bytes_and_uses_flat_checksum_paths(self):
        root = self.fixture()
        assets = release.prepare_assets(root, '1.0.0')
        self.assertEqual(len(assets), 4)
        for line in assets[-1].read_text().splitlines():
            sha, name = line.split()
            self.assertEqual(Path(name).name, name)
            self.assertEqual(release.digest(assets[-1].parent / name), sha)

    def test_tampered_artifact_is_rejected_before_release_staging(self):
        root = self.fixture()
        (root / 'build/libs/krc-villagers-1.0.0.jar').write_bytes(b'wrong build')
        with self.assertRaisesRegex(ValueError, 'SHA-256 mismatch'):
            release.prepare_assets(root, '1.0.0')
        self.assertFalse((root / 'build/release-assets').exists())

    def test_version_mismatch_or_unexpected_files_are_rejected(self):
        root = self.fixture()
        with self.assertRaisesRegex(ValueError, 'exactly the expected'):
            release.prepare_assets(root, '1.0.1')
        with (root / 'build/SHA256SUMS.txt').open('a') as out:
            out.write(hashlib.sha256(b'extra').hexdigest() + '  ../extra.jar\n')
        with self.assertRaisesRegex(ValueError, 'exactly the expected'):
            release.prepare_assets(root, '1.0.0')


if __name__ == '__main__':
    unittest.main()
