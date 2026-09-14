import copy
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile

spec = importlib.util.spec_from_file_location("updater", Path(__file__).resolve().parents[1] / "update-dependencies.py")
updater = importlib.util.module_from_spec(spec)
spec.loader.exec_module(updater)


class UpdateTests(unittest.TestCase):
    def setUp(self):
        # Fixture warnings belong in test output, not the real dependency-check summary.
        summary_env = patch.dict(updater.os.environ, {"GITHUB_STEP_SUMMARY": ""})
        summary_env.start()
        self.addCleanup(summary_env.stop)

    def test_curseforge_checks_loader_version_channel_and_file_id(self):
        current = {"file_id": 100, "channel": "release"}
        valid = {"id": 101, "versions": ["1.21.1", "NeoForge"], "type": "release"}
        files = [valid, {**valid, "id": 200, "versions": ["1.21.1", "Fabric"]},
                 {**valid, "id": 201, "versions": ["1.21.2", "NeoForge"]},
                 {**valid, "id": 202, "type": "alpha"}]
        self.assertEqual(updater.select_curseforge(files, current, "1.21.1"), valid)
        self.assertIsNone(updater.select_curseforge(files, {**current, "file_id": 102}, "1.21.1"))

    def test_modrinth_uses_publication_date_instead_of_version_string(self):
        current = {"version_id": "old", "published_at": "2026-09-12T00:00:00Z", "channel": "beta"}
        latest = {"id": "new", "date_published": "2026-09-13T00:00:00Z", "version_number": "1.0.0",
                  "game_versions": ["1.21.1"], "loaders": ["neoforge"], "version_type": "beta"}
        older = {**latest, "id": "older", "date_published": "2026-09-11T00:00:00Z", "version_number": "9.0.0"}
        fabric = {**latest, "id": "fabric", "loaders": ["fabric"], "date_published": "2026-09-14T00:00:00Z"}
        self.assertEqual(updater.select_modrinth([older, fabric, latest], current, "1.21.1"), latest)
        self.assertIsNone(updater.select_modrinth([older], current, "1.21.1"))

    def fixture(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        root = Path(temp.name)
        lock = {"minecraft": "1.21.1", "files": [
            {"modid": "test_mod", "version": "1.0.0", "path": "libs/test-old.jar", "sha256": "old",
             "source_url": "https://example.org/old", "update": {"file_id": 100}}]}
        (root / "dependencies.lock.json").write_text(json.dumps(lock))
        (root / "gradle.properties").write_text("mod_version=1.0.0\nneo_version=21.1.244\n")
        data = io.BytesIO()
        with ZipFile(data, "w") as jar:
            jar.writestr("META-INF/neoforge.mods.toml", 'license="MIT"\n[[mods]]\nmodId="test_mod"\nversion="1.0.0"\n')
        payload = data.getvalue()
        found = ("https://example.org/new.jar", "https://example.org/new", "test-new.jar", {"file_id": 101},
                 {"sha512": hashlib.sha512(payload).hexdigest()})
        return root, payload, found

    def test_same_version_reupload_updates_hash_and_bumps_addon_patch(self):
        root, payload, found = self.fixture()
        with patch.object(updater, "candidate", return_value=found), patch.object(updater, "fetch", return_value=payload):
            changes = updater.update(root)
        self.assertEqual(changes[0]["before"], changes[0]["after"])
        lock = json.loads((root / "dependencies.lock.json").read_text())
        self.assertEqual(lock["files"][0]["sha256"], hashlib.sha256(payload).hexdigest())
        self.assertEqual(lock["files"][0]["update"]["file_id"], 101)
        self.assertIn("mod_version=1.0.1", (root / "gradle.properties").read_text())
        self.assertEqual((root / "libs/test-new.jar").read_bytes(), payload)

    def test_no_updates_leaves_lock_and_version_byte_identical(self):
        root, _, _ = self.fixture()
        before = [(root / name).read_bytes() for name in ["dependencies.lock.json", "gradle.properties"]]
        with patch.object(updater, "candidate", return_value=None):
            self.assertEqual(updater.update(root), [])
        self.assertEqual(before, [(root / name).read_bytes() for name in ["dependencies.lock.json", "gradle.properties"]])

    def test_bad_upstream_hash_leaves_candidate_checkout_unchanged(self):
        root, payload, found = self.fixture()
        before = (root / "dependencies.lock.json").read_bytes()
        with patch.object(updater, "candidate", return_value=found), patch.object(updater, "fetch", return_value=payload + b"corruption"):
            with self.assertRaisesRegex(ValueError, "hash mismatch"):
                updater.update(root)
        self.assertEqual((root / "dependencies.lock.json").read_bytes(), before)
        self.assertIn("mod_version=1.0.0", (root / "gradle.properties").read_text())
        self.assertFalse((root / "libs/test-new.jar").exists())

    def test_failed_later_dependency_does_not_partially_update_lock(self):
        root, payload, found = self.fixture()
        lock = json.loads((root / "dependencies.lock.json").read_text())
        lock["files"].append({**copy.deepcopy(lock["files"][0]), "modid": "other"})
        (root / "dependencies.lock.json").write_text(json.dumps(lock))
        before = (root / "dependencies.lock.json").read_bytes()
        with patch.object(updater, "candidate", side_effect=[found, RuntimeError("API unavailable")]), patch.object(updater, "fetch", return_value=payload):
            with self.assertRaises(RuntimeError):
                updater.update(root)
        self.assertEqual((root / "dependencies.lock.json").read_bytes(), before)
        self.assertFalse((root / "libs/test-new.jar").exists())


if __name__ == "__main__":
    unittest.main()
