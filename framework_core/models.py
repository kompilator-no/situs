from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, List, Optional


class ExecutionStatus(str, Enum):
    PASSED = "PASSED"
    FAILED = "FAILED"
    SKIPPED = "SKIPPED"
    ERROR = "ERROR"


class RegressionStatus(str, Enum):
    NO_REGRESSION = "NO_REGRESSION"
    REGRESSION_DETECTED = "REGRESSION_DETECTED"
    FLAKY_SUSPECTED = "FLAKY_SUSPECTED"
    BASELINE_MISSING = "BASELINE_MISSING"


@dataclass(frozen=True)
class BaselineTag:
    environment: str
    release: Optional[str] = None
    labels: Dict[str, str] = field(default_factory=dict)

    def key(self) -> str:
        ordered_labels = ",".join(f"{k}={v}" for k, v in sorted(self.labels.items()))
        return f"env={self.environment}|release={self.release or 'latest'}|{ordered_labels}"


@dataclass
class StepResult:
    step_id: str
    status: ExecutionStatus
    latency_ms: Optional[float] = None
    payload: Any = None
    historical_statuses: List[ExecutionStatus] = field(default_factory=list)


@dataclass
class TestResult:
    test_id: str
    status: ExecutionStatus
    latency_ms: Optional[float] = None
    payload: Any = None
    steps: List[StepResult] = field(default_factory=list)
    historical_statuses: List[ExecutionStatus] = field(default_factory=list)


@dataclass
class RunResult:
    run_id: str
    suite_id: str
    status: ExecutionStatus
    tests: List[TestResult]


@dataclass
class BaselineSnapshot:
    created_at_epoch_ms: int
    run: RunResult


@dataclass
class RegressionThresholds:
    latency_relative_increase: float = 0.2
    latency_absolute_increase_ms: float = 100.0
    payload_drift_threshold: float = 0.15
