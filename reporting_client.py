from __future__ import annotations

import hashlib
import json
import random
import time
import uuid
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional
from urllib import error, request


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


@dataclass
class Attachment:
    name: str
    content_type: str
    uri: str


@dataclass
class StepResult:
    name: str
    status: str
    duration_ms: int
    error_message: Optional[str] = None
    attachments: List[Attachment] = field(default_factory=list)


@dataclass
class TestResult:
    test_id: str
    name: str
    status: str
    duration_ms: int
    error_message: Optional[str] = None
    attachments: List[Attachment] = field(default_factory=list)
    steps: List[StepResult] = field(default_factory=list)


@dataclass
class RunMetadata:
    run_id: str
    suite: str
    environment: str
    commit_id: Optional[str]
    build_id: Optional[str]
    started_at: str
    finished_at: Optional[str]


@dataclass
class Summary:
    passed: int
    failed: int
    skipped: int


@dataclass
class ReportPayload:
    schema_version: str
    metadata: RunMetadata
    tests: List[TestResult]
    summary: Summary

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


class FileBackedQueue:
    """Append-only JSONL fallback queue."""

    def __init__(self, path: str = ".reporting-fallback-queue.jsonl") -> None:
        self.path = Path(path)

    def enqueue(self, item: Dict[str, Any]) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self.path.open("a", encoding="utf-8") as f:
            f.write(json.dumps(item, separators=(",", ":")) + "\n")

    def drain(self) -> List[Dict[str, Any]]:
        if not self.path.exists():
            return []
        with self.path.open("r", encoding="utf-8") as f:
            lines = [line.strip() for line in f if line.strip()]
        self.path.unlink(missing_ok=True)

        drained: List[Dict[str, Any]] = []
        for line in lines:
            try:
                drained.append(json.loads(line))
            except json.JSONDecodeError:
                continue
        return drained


class ReportPublisher:
    def __init__(
        self,
        endpoint_url: str,
        *,
        queue: Optional[FileBackedQueue] = None,
        timeout_s: int = 5,
        max_retries: int = 4,
        backoff_base_s: float = 0.2,
        backoff_max_s: float = 5.0,
        user_agent: str = "reporting-client/1.0",
    ) -> None:
        self.endpoint_url = endpoint_url
        self.queue = queue or FileBackedQueue()
        self.timeout_s = timeout_s
        self.max_retries = max_retries
        self.backoff_base_s = backoff_base_s
        self.backoff_max_s = backoff_max_s
        self.user_agent = user_agent

    def publish(self, report: ReportPayload) -> bool:
        payload = report.to_dict()
        idempotency_key = self._build_idempotency_key(payload)
        envelope = {
            "idempotency_key": idempotency_key,
            "payload": payload,
            "queued_at": utc_now_iso(),
        }

        # Try queued events first to preserve ordering and eventually flush backlog.
        queued = self.queue.drain()
        for queued_item in queued:
            if not self._send_with_retry(queued_item["payload"], queued_item["idempotency_key"]):
                self.queue.enqueue(queued_item)
                self.queue.enqueue(envelope)
                return False

        if not self._send_with_retry(payload, idempotency_key):
            self.queue.enqueue(envelope)
            return False

        return True

    def _send_with_retry(self, payload: Dict[str, Any], idempotency_key: str) -> bool:
        data = json.dumps(payload).encode("utf-8")
        for attempt in range(self.max_retries + 1):
            try:
                req = request.Request(
                    self.endpoint_url,
                    data=data,
                    headers={
                        "Content-Type": "application/json",
                        "Accept": "application/json",
                        "Idempotency-Key": idempotency_key,
                        "User-Agent": self.user_agent,
                    },
                    method="POST",
                )
                with request.urlopen(req, timeout=self.timeout_s) as resp:
                    status = getattr(resp, "status", 200)
                    if 200 <= status < 300:
                        return True
                    if not self._is_retryable_status(status):
                        return False
            except error.HTTPError as exc:
                if not self._is_retryable_status(exc.code):
                    return False
            except (error.URLError, TimeoutError):
                pass

            if attempt < self.max_retries:
                time.sleep(self._compute_backoff(attempt))

        return False

    def _compute_backoff(self, attempt: int) -> float:
        base = self.backoff_base_s * (2 ** attempt)
        jitter = random.uniform(0, self.backoff_base_s)
        return min(self.backoff_max_s, base + jitter)

    @staticmethod
    def _is_retryable_status(status_code: int) -> bool:
        return status_code == 429 or 500 <= status_code < 600

    @staticmethod
    def _build_idempotency_key(payload: Dict[str, Any]) -> str:
        run_id = payload.get("metadata", {}).get("run_id") or str(uuid.uuid4())
        digest = hashlib.sha256(json.dumps(payload, sort_keys=True).encode("utf-8")).hexdigest()
        return f"{run_id}:{digest[:24]}"


def build_summary(tests: List[TestResult]) -> Summary:
    passed = sum(1 for t in tests if t.status == "passed")
    failed = sum(1 for t in tests if t.status == "failed")
    skipped = sum(1 for t in tests if t.status == "skipped")
    return Summary(passed=passed, failed=failed, skipped=skipped)


def new_report(
    *,
    suite: str,
    environment: str,
    tests: List[TestResult],
    commit_id: Optional[str] = None,
    build_id: Optional[str] = None,
    run_id: Optional[str] = None,
    started_at: Optional[str] = None,
    finished_at: Optional[str] = None,
    schema_version: str = "v1",
) -> ReportPayload:
    return ReportPayload(
        schema_version=schema_version,
        metadata=RunMetadata(
            run_id=run_id or str(uuid.uuid4()),
            suite=suite,
            environment=environment,
            commit_id=commit_id,
            build_id=build_id,
            started_at=started_at or utc_now_iso(),
            finished_at=finished_at,
        ),
        tests=tests,
        summary=build_summary(tests),
    )
