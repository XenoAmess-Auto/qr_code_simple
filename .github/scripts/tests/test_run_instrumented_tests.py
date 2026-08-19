import os
import re
import signal
import subprocess
import tempfile
import time
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
RUNNER = ROOT / ".github" / "scripts" / "run-instrumented-tests.sh"
WORKFLOW = ROOT / ".github" / "workflows" / "build.yml"


class InstrumentedTestRunnerContractTest(unittest.TestCase):
    def make_command(self, path, body):
        path.write_text("#!/usr/bin/env bash\n" + body, encoding="utf-8")
        path.chmod(0o755)

    def environment(self, bin_dir, gradle_timeout="5"):
        environment = os.environ.copy()
        environment["PATH"] = f"{bin_dir}:{environment['PATH']}"
        environment["GRADLE_TIMEOUT_SECONDS"] = gradle_timeout
        environment["ADB_TIMEOUT_SECONDS"] = "2"
        return environment

    def wait_for_file(self, path, process):
        deadline = time.monotonic() + 5
        while not path.exists() and time.monotonic() < deadline:
            self.assertIsNone(process.poll(), "runner exited before Gradle started")
            time.sleep(0.01)
        self.assertTrue(path.exists(), "Gradle did not start")

    def test_assertion_failure_is_single_attempt_and_every_adb_is_bounded(self):
        with tempfile.TemporaryDirectory() as directory:
            temp = Path(directory)
            bin_dir = temp / "bin"
            bin_dir.mkdir()
            gradle_calls = temp / "gradle-calls.txt"
            adb_calls = temp / "adb-calls.txt"
            timeout_calls = temp / "timeout-calls.txt"

            self.make_command(
                temp / "gradlew",
                f"printf 'call\\n' >> {gradle_calls!s}\nexit 1\n",
            )
            self.make_command(
                bin_dir / "adb",
                f"printf '%s\\n' \"$*\" >> {adb_calls!s}\nexit 0\n",
            )
            self.make_command(
                bin_dir / "timeout",
                f"printf '%s\\n' \"$*\" >> {timeout_calls!s}\n"
                "while [[ $# -gt 0 && $1 == -* ]]; do shift; done\n"
                "shift\n"
                "exec \"$@\"\n",
            )

            result = subprocess.run(
                ["bash", "-x", str(RUNNER)],
                cwd=temp,
                env=self.environment(bin_dir),
                check=False,
                capture_output=True,
                text=True,
                timeout=15,
            )

            self.assertEqual(1, result.returncode)
            self.assertEqual(["call"], gradle_calls.read_text(encoding="utf-8").splitlines())
            adb_invocations = adb_calls.read_text(encoding="utf-8").splitlines()
            bounded_adb_invocations = [
                call for call in timeout_calls.read_text(encoding="utf-8").splitlines()
                if call.startswith("--signal=KILL 2s adb ")
            ]
            routed_adb_invocations = [
                line for line in result.stderr.splitlines()
                if line.startswith("+ run_adb ")
            ]
            self.assertGreaterEqual(len(adb_invocations), 7)
            self.assertEqual(len(adb_invocations), len(bounded_adb_invocations))
            self.assertEqual(len(adb_invocations), len(routed_adb_invocations))

    def test_gradle_timeout_returns_timeout_status_and_collects_diagnostics(self):
        with tempfile.TemporaryDirectory() as directory:
            temp = Path(directory)
            bin_dir = temp / "bin"
            bin_dir.mkdir()
            gradle_calls = temp / "gradle-calls.txt"
            adb_calls = temp / "adb-calls.txt"
            self.make_command(
                temp / "gradlew",
                f"printf 'call\\n' >> {gradle_calls!s}\nwhile :; do sleep 1; done\n",
            )
            self.make_command(
                bin_dir / "adb",
                f"printf '%s\\n' \"$*\" >> {adb_calls!s}\nexit 0\n",
            )

            result = subprocess.run(
                ["bash", str(RUNNER)],
                cwd=temp,
                env=self.environment(bin_dir, gradle_timeout="0.2"),
                check=False,
                timeout=10,
            )

            self.assertEqual(124, result.returncode)
            self.assertEqual(["call"], gradle_calls.read_text(encoding="utf-8").splitlines())
            self.assertGreaterEqual(len(adb_calls.read_text(encoding="utf-8").splitlines()), 7)

    def assert_signal_status(self, sent_signal, expected_status):
        with tempfile.TemporaryDirectory() as directory:
            temp = Path(directory)
            bin_dir = temp / "bin"
            bin_dir.mkdir()
            started = temp / "gradle-started.txt"
            adb_calls = temp / "adb-calls.txt"
            self.make_command(
                temp / "gradlew",
                f"touch {started!s}\ntrap 'exit 0' TERM INT\nwhile :; do sleep 1; done\n",
            )
            self.make_command(
                bin_dir / "adb",
                f"printf '%s\\n' \"$*\" >> {adb_calls!s}\nexit 0\n",
            )
            process = subprocess.Popen(
                ["bash", str(RUNNER)],
                cwd=temp,
                env=self.environment(bin_dir, gradle_timeout="30"),
                start_new_session=True,
            )
            try:
                self.wait_for_file(started, process)
                process.send_signal(sent_signal)
                self.assertEqual(expected_status, process.wait(timeout=10))
                self.assertGreaterEqual(len(adb_calls.read_text(encoding="utf-8").splitlines()), 7)
            finally:
                if process.poll() is None:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()

    def test_term_and_int_preserve_shell_exit_statuses(self):
        self.assert_signal_status(signal.SIGTERM, 143)
        self.assert_signal_status(signal.SIGINT, 130)

    def test_runner_has_valid_bash_syntax(self):
        result = subprocess.run(["bash", "-n", str(RUNNER)], check=False)
        self.assertEqual(0, result.returncode)

    def test_workflow_bounds_instrumented_job_and_emulator_step(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        job = re.search(
            r"^  android-test:\n(?P<body>.*?)(?=^  [a-z][a-z0-9-]*:\n)",
            workflow,
            flags=re.MULTILINE | re.DOTALL,
        )
        self.assertIsNotNone(job)
        job_body = job.group("body")
        self.assertIn("    timeout-minutes: 75", job_body)

        emulator_step = re.search(
            r"      - name: Run instrumented tests on emulator\n(?P<body>.*?)(?=\n      - name:)",
            job_body,
            flags=re.DOTALL,
        )
        self.assertIsNotNone(emulator_step)
        self.assertIn("        timeout-minutes: 60", emulator_step.group("body"))
        self.assertIn("script: bash .github/scripts/run-instrumented-tests.sh", emulator_step.group("body"))


if __name__ == "__main__":
    unittest.main()
