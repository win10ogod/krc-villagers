import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("committer", Path(__file__).resolve().parents[1] / "commit-updates.py")
committer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(committer)


class CommitTests(unittest.TestCase):
    def git(self, root, *args):
        return subprocess.check_output(['git', *args], cwd=root, text=True, stderr=subprocess.DEVNULL).strip()

    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.remote = self.root / 'remote.git'
        self.git(self.root, 'init', '--bare', '--initial-branch=main', str(self.remote))
        self.work = self.root / 'candidate'
        self.git(self.root, 'clone', str(self.remote), str(self.work))
        self.git(self.work, 'config', 'user.name', 'Test')
        self.git(self.work, 'config', 'user.email', 'test@example.invalid')
        for name in ['dependencies.lock.json', 'gradle.properties', 'source.java']:
            (self.work / name).write_text('original\n')
        self.git(self.work, 'add', '.')
        self.git(self.work, 'commit', '-m', 'base')
        self.git(self.work, 'push', 'origin', 'main')
        self.base = self.git(self.work, 'rev-parse', 'HEAD')
        self.update_candidate()

    def update_candidate(self):
        for name in ['dependencies.lock.json', 'gradle.properties']:
            (self.work / name).write_text('tested update\n')

    def test_commits_metadata_and_reuses_exact_commit_after_publish_interruption(self):
        first = committer.commit_updates(self.work, self.base)
        self.assertNotEqual(first, self.base)
        self.assertEqual(self.git(self.work, 'ls-remote', 'origin', 'refs/heads/main').split()[0], first)
        self.git(self.work, 'checkout', '--detach', self.base)
        self.update_candidate()
        retried = committer.commit_updates(self.work, self.base)
        self.assertEqual(first, retried)
        self.assertEqual(self.git(self.work, 'rev-parse', 'HEAD'), self.base)

    def test_new_user_commit_is_not_overwritten(self):
        other = self.root / 'other'
        self.git(self.root, 'clone', str(self.remote), str(other))
        self.git(other, 'config', 'user.name', 'Test')
        self.git(other, 'config', 'user.email', 'test@example.invalid')
        (other / 'source.java').write_text('new user change\n')
        self.git(other, 'commit', '-am', 'new user change')
        self.git(other, 'push', 'origin', 'main')
        head = self.git(other, 'rev-parse', 'HEAD')
        with self.assertRaisesRegex(ValueError, 'main changed'):
            committer.commit_updates(self.work, self.base)
        self.assertEqual(self.git(self.work, 'ls-remote', 'origin', 'refs/heads/main').split()[0], head)


if __name__ == '__main__':
    unittest.main()
