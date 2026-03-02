import unittest

from framework_core import (
    BaselineStore,
    BaselineTag,
    ExecutionStatus,
    RegressionAnalyzer,
    RegressionStatus,
    RunResult,
    StepResult,
    TestResult,
)


class RegressionTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tag = BaselineTag(environment="staging", release="2026.03")
        self.store = BaselineStore()
        self.analyzer = RegressionAnalyzer()

    def test_detects_status_latency_and_payload_regressions(self) -> None:
        baseline = RunResult(
            run_id="base-1",
            suite_id="checkout",
            status=ExecutionStatus.PASSED,
            tests=[
                TestResult(
                    test_id="t1",
                    status=ExecutionStatus.PASSED,
                    latency_ms=200,
                    payload={"value": "ok"},
                    steps=[
                        StepResult(step_id="s1", status=ExecutionStatus.PASSED, latency_ms=90, payload={"token": "aaaa"})
                    ],
                )
            ],
        )
        self.store.save_baseline(self.tag, baseline, created_at_epoch_ms=1)

        current = RunResult(
            run_id="cur-1",
            suite_id="checkout",
            status=ExecutionStatus.FAILED,
            tests=[
                TestResult(
                    test_id="t1",
                    status=ExecutionStatus.FAILED,
                    latency_ms=500,
                    payload={"value": "major-drift"},
                    steps=[
                        StepResult(step_id="s1", status=ExecutionStatus.PASSED, latency_ms=250, payload={"token": "zzzz"})
                    ],
                )
            ],
        )

        payload = self.analyzer.compare_to_baseline(current, self.store.get_latest_baseline(self.tag))
        self.assertEqual(payload["regression_status"], RegressionStatus.REGRESSION_DETECTED.value)
        self.assertEqual(payload["tests"][0]["regression_status"], RegressionStatus.REGRESSION_DETECTED.value)
        self.assertEqual(payload["tests"][0]["steps"][0]["regression_status"], RegressionStatus.REGRESSION_DETECTED.value)

    def test_marks_flaky_when_status_toggles(self) -> None:
        baseline = RunResult(
            run_id="base-2",
            suite_id="payments",
            status=ExecutionStatus.PASSED,
            tests=[
                TestResult(
                    test_id="t2",
                    status=ExecutionStatus.FAILED,
                    historical_statuses=[ExecutionStatus.PASSED, ExecutionStatus.FAILED],
                )
            ],
        )
        current = RunResult(
            run_id="cur-2",
            suite_id="payments",
            status=ExecutionStatus.PASSED,
            tests=[TestResult(test_id="t2", status=ExecutionStatus.PASSED)],
        )

        payload = self.analyzer.compare_to_baseline(current, self.store.save_baseline(self.tag, baseline, 2))
        self.assertEqual(payload["tests"][0]["regression_status"], RegressionStatus.FLAKY_SUSPECTED.value)

    def test_missing_baseline(self) -> None:
        current = RunResult(run_id="cur-3", suite_id="search", status=ExecutionStatus.PASSED, tests=[])
        payload = self.analyzer.compare_to_baseline(current, None)
        self.assertEqual(payload["regression_status"], RegressionStatus.BASELINE_MISSING.value)


if __name__ == "__main__":
    unittest.main()
