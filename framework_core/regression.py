from __future__ import annotations

import json
import time
from dataclasses import asdict
from difflib import SequenceMatcher
from typing import Any, Dict, List, Optional, Tuple

from .models import (
    BaselineSnapshot,
    BaselineTag,
    ExecutionStatus,
    RegressionStatus,
    RegressionThresholds,
    RunResult,
    StepResult,
    TestResult,
)


class BaselineStore:
    """In-memory baseline storage with support for environment/release tags."""

    def __init__(self) -> None:
        self._snapshots_by_tag: Dict[str, List[BaselineSnapshot]] = {}

    def save_baseline(self, tag: BaselineTag, run: RunResult, created_at_epoch_ms: Optional[int] = None) -> BaselineSnapshot:
        snapshot = BaselineSnapshot(
            created_at_epoch_ms=created_at_epoch_ms or int(time.time() * 1000),
            run=run,
        )
        key = tag.key()
        self._snapshots_by_tag.setdefault(key, []).append(snapshot)
        self._snapshots_by_tag[key].sort(key=lambda s: s.created_at_epoch_ms)
        return snapshot

    def get_latest_baseline(self, tag: BaselineTag) -> Optional[BaselineSnapshot]:
        snapshots = self._snapshots_by_tag.get(tag.key(), [])
        return snapshots[-1] if snapshots else None


class RegressionAnalyzer:
    def __init__(self, thresholds: Optional[RegressionThresholds] = None) -> None:
        self.thresholds = thresholds or RegressionThresholds()

    def compare_to_baseline(self, current: RunResult, baseline: Optional[BaselineSnapshot]) -> Dict[str, Any]:
        if baseline is None:
            return {
                "run_id": current.run_id,
                "suite_id": current.suite_id,
                "regression_status": RegressionStatus.BASELINE_MISSING.value,
                "regression_reasons": ["No baseline found for provided baseline tag"],
                "tests": [
                    {
                        "test_id": t.test_id,
                        "regression_status": RegressionStatus.BASELINE_MISSING.value,
                        "regression_reasons": ["No baseline test for comparison"],
                        "steps": [
                            {
                                "step_id": s.step_id,
                                "regression_status": RegressionStatus.BASELINE_MISSING.value,
                                "regression_reasons": ["No baseline step for comparison"],
                            }
                            for s in t.steps
                        ],
                    }
                    for t in current.tests
                ],
            }

        baseline_tests = {t.test_id: t for t in baseline.run.tests}
        suite_reasons: List[str] = []
        suite_status = RegressionStatus.NO_REGRESSION
        tests_payload: List[Dict[str, Any]] = []

        for test in current.tests:
            test_payload, test_status, reasons = self._compare_test(test, baseline_tests.get(test.test_id))
            tests_payload.append(test_payload)
            if test_status == RegressionStatus.REGRESSION_DETECTED:
                suite_status = RegressionStatus.REGRESSION_DETECTED
                suite_reasons.extend(f"{test.test_id}: {r}" for r in reasons)
            elif test_status == RegressionStatus.FLAKY_SUSPECTED and suite_status != RegressionStatus.REGRESSION_DETECTED:
                suite_status = RegressionStatus.FLAKY_SUSPECTED
                suite_reasons.extend(f"{test.test_id}: {r}" for r in reasons)

        return {
            "run_id": current.run_id,
            "suite_id": current.suite_id,
            "baseline_run_id": baseline.run.run_id,
            "regression_status": suite_status.value,
            "regression_reasons": suite_reasons,
            "thresholds": asdict(self.thresholds),
            "tests": tests_payload,
        }

    def _compare_test(self, current: TestResult, baseline: Optional[TestResult]) -> Tuple[Dict[str, Any], RegressionStatus, List[str]]:
        if baseline is None:
            payload = {
                "test_id": current.test_id,
                "regression_status": RegressionStatus.BASELINE_MISSING.value,
                "regression_reasons": ["Baseline test is missing"],
                "steps": [
                    {
                        "step_id": s.step_id,
                        "regression_status": RegressionStatus.BASELINE_MISSING.value,
                        "regression_reasons": ["Baseline step is missing"],
                    }
                    for s in current.steps
                ],
            }
            return payload, RegressionStatus.BASELINE_MISSING, ["Baseline test is missing"]

        reasons, status = self._evaluate_level(current, baseline, "test")
        baseline_steps = {s.step_id: s for s in baseline.steps}
        step_payloads: List[Dict[str, Any]] = []

        for step in current.steps:
            step_reasons, step_status = self._evaluate_level(step, baseline_steps.get(step.step_id), "step")
            step_payloads.append(
                {
                    "step_id": step.step_id,
                    "regression_status": step_status.value,
                    "regression_reasons": step_reasons,
                }
            )
            if step_status == RegressionStatus.REGRESSION_DETECTED:
                if status != RegressionStatus.REGRESSION_DETECTED:
                    status = RegressionStatus.REGRESSION_DETECTED
                reasons.extend(f"step:{step.step_id} {r}" for r in step_reasons)
            elif step_status == RegressionStatus.FLAKY_SUSPECTED and status == RegressionStatus.NO_REGRESSION:
                status = RegressionStatus.FLAKY_SUSPECTED
                reasons.extend(f"step:{step.step_id} {r}" for r in step_reasons)

        return {
            "test_id": current.test_id,
            "regression_status": status.value,
            "regression_reasons": reasons,
            "steps": step_payloads,
        }, status, reasons

    def _evaluate_level(
        self,
        current: TestResult | StepResult,
        baseline: Optional[TestResult | StepResult],
        level: str,
    ) -> Tuple[List[str], RegressionStatus]:
        if baseline is None:
            return [f"Baseline {level} is missing"], RegressionStatus.BASELINE_MISSING

        reasons: List[str] = []
        status = RegressionStatus.NO_REGRESSION

        if self._is_status_regression(current.status, baseline.status):
            reasons.append(f"Status changed from {baseline.status.value} to {current.status.value}")
            status = RegressionStatus.REGRESSION_DETECTED
        elif self._is_flaky_suspected(current, baseline):
            reasons.append(f"Status toggles detected between baseline/history and current: {current.status.value}")
            status = RegressionStatus.FLAKY_SUSPECTED

        latency_reason = self._latency_regression_reason(current.latency_ms, baseline.latency_ms)
        if latency_reason:
            reasons.append(latency_reason)
            status = RegressionStatus.REGRESSION_DETECTED

        payload_reason = self._payload_regression_reason(current.payload, baseline.payload)
        if payload_reason:
            reasons.append(payload_reason)
            status = RegressionStatus.REGRESSION_DETECTED

        return reasons, status

    @staticmethod
    def _is_status_regression(current: ExecutionStatus, baseline: ExecutionStatus) -> bool:
        return baseline == ExecutionStatus.PASSED and current in {ExecutionStatus.FAILED, ExecutionStatus.ERROR}

    @staticmethod
    def _is_flaky_suspected(current: TestResult | StepResult, baseline: TestResult | StepResult) -> bool:
        if current.status == baseline.status:
            return False
        history = set(baseline.historical_statuses + current.historical_statuses)
        history.update({baseline.status, current.status})
        return len(history) > 1 and current.status in history and baseline.status in history

    def _latency_regression_reason(self, current_ms: Optional[float], baseline_ms: Optional[float]) -> Optional[str]:
        if current_ms is None or baseline_ms is None or baseline_ms <= 0:
            return None

        delta = current_ms - baseline_ms
        relative = delta / baseline_ms
        if delta >= self.thresholds.latency_absolute_increase_ms and relative >= self.thresholds.latency_relative_increase:
            return (
                f"Latency regression: baseline={baseline_ms:.1f}ms current={current_ms:.1f}ms "
                f"delta={delta:.1f}ms ({relative * 100:.1f}% increase)"
            )
        return None

    def _payload_regression_reason(self, current_payload: Any, baseline_payload: Any) -> Optional[str]:
        if current_payload is None or baseline_payload is None:
            return None

        baseline_json = json.dumps(baseline_payload, sort_keys=True, ensure_ascii=True)
        current_json = json.dumps(current_payload, sort_keys=True, ensure_ascii=True)
        similarity = SequenceMatcher(a=baseline_json, b=current_json).ratio()
        drift = 1 - similarity
        if drift >= self.thresholds.payload_drift_threshold:
            return f"Payload regression: drift={drift:.3f} exceeded threshold={self.thresholds.payload_drift_threshold:.3f}"
        return None
