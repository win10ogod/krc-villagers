import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("tracker", Path(__file__).resolve().parents[1] / "track-rejected-update.py")
tracker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tracker)

OLD, NEW, NEWER = (f"https://example.org/files/{n}" for n in (100, 101, 102))
CHANGE = {"modid": "test_mod", "before": "1.0.0", "after": "1.0.1", "before_source": OLD, "after_source": NEW}


class FakeGitHub:
    def __init__(self, issues):
        self.issues = issues
        self.calls = []

    def __call__(self, *args):
        self.calls.append(args)
        if args[:2] == ("issue", "list"):
            return json.dumps(self.issues)
        if args[:2] == ("issue", "create"):
            return "https://github.com/example/repo/issues/9"
        return ""

    def actions(self, name):
        return [call for call in self.calls if call[:2] == ("issue", name)]


def issue_for(number, change):
    body = "text\n" + tracker.MARKER + json.dumps(tracker.candidate_key([change]), sort_keys=True) + " -->"
    return {"number": number, "body": body}


class SummaryTests(unittest.TestCase):
    def test_summary_lists_candidates_and_only_failure_lines(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "build/ci").mkdir(parents=True)
            (root / "build/ci/dependency-updates.json").write_text(json.dumps([CHANGE]))
            (root / "build/ci/gradle.log").write_text("\n".join([
                "\x1b[1m*** Started working on decompile\x1b[0m",
                "Failed to request yggdrasil public key",
                "/home/runner/work/x/x/src/main/java/dev/A.java:18: error: cannot find symbol",
                "        double value = Attributes.ABILITY_METER;",
                "                       ^",
                "  symbol:   class Attributes",
                "  location: package com.example",
                "> Task :compileJava FAILED",
                "  /home/runner/work/x/x/src/main/java/dev/A.java:18: error: cannot find symbol",
                "[Server thread/ERROR] [net.minecraft.gametest.framework.LogTestReporter/]: kick failed at 1, 2, 3! energy",
                "[Server thread/INFO] [minecraft/GameTestServer]: 6 required tests failed :(",
            ]))
            changes, summary = tracker.summarize(root)
        self.assertEqual(changes, [CHANGE])
        self.assertIn(f"[1.0.1]({NEW})", summary)
        self.assertEqual(summary.count("src/main/java/dev/A.java:18: error: cannot find symbol"), 1)
        for expected in ("symbol:   class Attributes", "> Task :compileJava FAILED", "kick failed at", "6 required tests failed"):
            self.assertIn(expected, summary)
        self.assertNotIn("yggdrasil", summary)
        self.assertNotIn("/home/runner", summary)

    def test_summary_requires_a_prepared_candidate(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "build/ci").mkdir(parents=True)
            (root / "build/ci/dependency-updates.json").write_text("[]")
            with self.assertRaisesRegex(ValueError, "not a rejected update"):
                tracker.summarize(root)


class TrackTests(unittest.TestCase):
    def run_track(self, issues, changes, locked):
        github = FakeGitHub(issues)
        with patch.object(tracker, "gh", github):
            tracker.track(changes, locked, "https://example.org/run")
        return github

    def test_first_rejection_opens_one_issue_with_marker(self):
        github = self.run_track([{"number": 1, "body": "unrelated"}], [CHANGE], {"test_mod": OLD})
        (create,) = github.actions("create")
        body = create[create.index("--body") + 1]
        self.assertIn(tracker.MARKER, body)
        self.assertIn("test_mod 1.0.0 → 1.0.1", create[create.index("--title") + 1])
        self.assertEqual(github.actions("close"), [])

    def test_same_rejection_on_later_schedule_does_not_duplicate(self):
        github = self.run_track([issue_for(4, CHANGE)], [CHANGE], {"test_mod": OLD})
        self.assertEqual(github.actions("create"), [])
        self.assertEqual(github.actions("close"), [])

    def test_newer_rejected_candidate_replaces_older_issue(self):
        newer = {**CHANGE, "after": "1.0.2", "after_source": NEWER}
        github = self.run_track([issue_for(4, CHANGE)], [newer], {"test_mod": OLD})
        (close,) = github.actions("close")
        self.assertEqual(close[2], "4")
        self.assertIn("not planned", close)
        self.assertEqual(len(github.actions("create")), 1)

    def test_publication_closes_issue_once_lock_moved_past_the_old_file(self):
        github = self.run_track([issue_for(4, CHANGE), {"number": 5, "body": "unrelated"}], [], {"test_mod": NEW})
        (close,) = github.actions("close")
        self.assertEqual(close[2], "4")
        self.assertIn("completed", close)
        self.assertEqual(github.actions("create"), [])

    def test_publication_without_the_candidate_keeps_issue_open(self):
        github = self.run_track([issue_for(4, CHANGE)], [], {"test_mod": OLD})
        self.assertEqual(github.actions("close"), [])
        self.assertEqual(github.actions("create"), [])


if __name__ == "__main__":
    unittest.main()
