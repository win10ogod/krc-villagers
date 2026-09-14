"""Regression tests for dependency integrity and interrupted downloads; no network needed."""
import hashlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import URLError

spec = importlib.util.spec_from_file_location("import_dependencies", Path(__file__).resolve().parents[1] / "import-dependencies.py")
importer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(importer)


class Response(io.BytesIO):
    def geturl(self):
        return "https://cdn.example.org/mod.jar"


class InterruptedResponse(Response):
    def read(self, size=-1):
        if self.tell():
            raise ConnectionResetError("download interrupted")
        return super().read(size)


class DownloadTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.target = self.root / "libs/mod.jar"
        self.content = b"exact locked dependency bytes"
        self.entry = {
            "path": "libs/mod.jar",
            "sha256": hashlib.sha256(self.content).hexdigest(),
            "download_url": "https://cdn.example.org/mod.jar",
        }
        self.sleep = patch.object(importer.time, "sleep").start()
        self.addCleanup(patch.stopall)

    def assert_no_partial_download(self):
        self.assertFalse(self.target.exists())
        self.assertEqual(list(self.root.rglob("*.part")), [])

    def test_verified_download_is_installed_and_can_be_reused_offline(self):
        with patch.object(importer, "urlopen", return_value=Response(self.content)) as request:
            importer.download(self.entry, self.target)
            importer.download(self.entry, self.target)
        request.assert_called_once()
        self.assertEqual(self.target.read_bytes(), self.content)
        self.assertEqual(list(self.root.rglob("*.part")), [])

    def test_hash_mismatch_fails_without_installing_or_retrying(self):
        with patch.object(importer, "urlopen", return_value=Response(b"wrong version")) as request:
            with self.assertRaisesRegex(ValueError, "SHA-256 differs"):
                importer.download(self.entry, self.target)
        request.assert_called_once()
        self.assert_no_partial_download()

    def test_conflicting_local_file_is_preserved_without_network(self):
        self.target.parent.mkdir()
        self.target.write_bytes(b"user's different dependency")
        with patch.object(importer, "urlopen") as request:
            with self.assertRaisesRegex(ValueError, "different file already exists"):
                importer.download(self.entry, self.target)
        request.assert_not_called()
        self.assertEqual(self.target.read_bytes(), b"user's different dependency")

    def test_interrupted_download_retries_with_a_fresh_file(self):
        with patch.object(importer, "urlopen", side_effect=[InterruptedResponse(b"partial"), Response(self.content)]) as request:
            importer.download(self.entry, self.target)
        self.assertEqual(request.call_count, 2)
        self.assertEqual(self.target.read_bytes(), self.content)
        self.assertEqual(list(self.root.rglob("*.part")), [])

    def test_network_failure_is_reported_after_three_attempts(self):
        with patch.object(importer, "urlopen", side_effect=URLError("unavailable")) as request:
            with self.assertRaises(URLError):
                importer.download(self.entry, self.target)
        self.assertEqual(request.call_count, 3)
        self.assert_no_partial_download()

    def test_insecure_source_is_rejected_before_network(self):
        self.entry["download_url"] = "http://cdn.example.org/mod.jar"
        with patch.object(importer, "urlopen") as request:
            with self.assertRaisesRegex(ValueError, "must use HTTPS"):
                importer.download(self.entry, self.target)
        request.assert_not_called()
        self.assert_no_partial_download()

    def test_insecure_redirect_is_rejected_without_installing(self):
        response = Response(self.content)
        response.geturl = lambda: "http://cdn.example.org/mod.jar"
        with patch.object(importer, "urlopen", return_value=response):
            with self.assertRaisesRegex(ValueError, "non-HTTPS"):
                importer.download(self.entry, self.target)
        self.assert_no_partial_download()


if __name__ == "__main__":
    unittest.main()
