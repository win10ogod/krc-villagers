from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest


class BuildWorkflowTests(unittest.TestCase):
    def run_build_step(self, status):
        workflow = (Path(__file__).resolve().parents[2] / '.github/workflows/build.yml').read_text()
        step = workflow.split('      - name: Build and run all GameTests\n', 1)[1].split('\n      - name:', 1)[0]
        script = textwrap.dedent(step.split('        run: |\n', 1)[1])
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            wrapper = root / 'gradlew'
            wrapper.write_text(f'#!/bin/sh\necho "fixture Gradle status {status}"\nexit {status}\n')
            wrapper.chmod(0o755)
            # Also works in the previous runner's bash -e environment: tee must never hide failure.
            result = subprocess.run(['bash', '-e', '-c', script], cwd=root, capture_output=True, text=True)
            log = (root / 'build/ci/gradle.log').read_text()
        return result, log

    def test_gradle_failure_stops_build_while_retaining_diagnostics(self):
        result, log = self.run_build_step(23)
        self.assertEqual(result.returncode, 23)
        self.assertIn('fixture Gradle status 23', log)

    def test_successful_build_can_continue_to_packaging(self):
        result, log = self.run_build_step(0)
        self.assertEqual(result.returncode, 0)
        self.assertIn('fixture Gradle status 0', log)
