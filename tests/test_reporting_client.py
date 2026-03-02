import json
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

from reporting_client import FileBackedQueue, StepResult, TestResult, new_report, ReportPublisher


class _Handler(BaseHTTPRequestHandler):
    responses = []
    received = []

    def do_POST(self):
        content_len = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(content_len).decode("utf-8")
        _Handler.received.append(
            {
                "path": self.path,
                "idempotency_key": self.headers.get("Idempotency-Key"),
                "body": json.loads(body),
            }
        )
        status = _Handler.responses.pop(0) if _Handler.responses else 200
        self.send_response(status)
        self.end_headers()

    def log_message(self, format, *args):
        return


class ReportingClientTests(unittest.TestCase):
    def setUp(self):
        _Handler.responses = []
        _Handler.received = []

    def _server(self):
        server = HTTPServer(("127.0.0.1", 0), _Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        return server

    def _sample_report(self):
        return new_report(
            suite="regression",
            environment="ci",
            commit_id="abc123",
            build_id="build-77",
            tests=[
                TestResult(
                    test_id="T-1",
                    name="happy path",
                    status="passed",
                    duration_ms=120,
                    steps=[StepResult(name="open", status="passed", duration_ms=20)],
                ),
                TestResult(
                    test_id="T-2",
                    name="failing path",
                    status="failed",
                    duration_ms=99,
                    error_message="assertion failed",
                ),
            ],
            finished_at="2026-01-01T00:00:01Z",
            run_id="run-1",
        )

    def test_publish_success(self):
        server = self._server()
        try:
            q = FileBackedQueue(path=tempfile.mktemp())
            publisher = ReportPublisher(f"http://127.0.0.1:{server.server_port}/reports", queue=q)
            ok = publisher.publish(self._sample_report())
            self.assertTrue(ok)
            self.assertEqual(len(_Handler.received), 1)
            received = _Handler.received[0]
            self.assertIn("run-1", received["idempotency_key"])
            self.assertEqual(received["body"]["summary"]["passed"], 1)
            self.assertEqual(received["body"]["summary"]["failed"], 1)
            self.assertEqual(received["body"]["schema_version"], "v1")
        finally:
            server.shutdown()
            server.server_close()

    def test_fallback_queue_then_drain(self):
        with tempfile.TemporaryDirectory() as tmp:
            queue_path = str(Path(tmp) / "queue.jsonl")
            q = FileBackedQueue(path=queue_path)
            publisher = ReportPublisher("http://127.0.0.1:1/reports", queue=q, max_retries=1, backoff_base_s=0)

            ok = publisher.publish(self._sample_report())
            self.assertFalse(ok)
            self.assertTrue(Path(queue_path).exists())

            server = self._server()
            try:
                publisher2 = ReportPublisher(
                    f"http://127.0.0.1:{server.server_port}/reports", queue=q, max_retries=1, backoff_base_s=0
                )
                ok2 = publisher2.publish(self._sample_report())
                self.assertTrue(ok2)
                # previous queued + current event
                self.assertEqual(len(_Handler.received), 2)
                self.assertFalse(Path(queue_path).exists())
            finally:
                server.shutdown()
            server.server_close()

    def test_retryable_status_eventually_succeeds(self):
        server = self._server()
        _Handler.responses = [500, 503, 200]
        try:
            q = FileBackedQueue(path=tempfile.mktemp())
            publisher = ReportPublisher(
                f"http://127.0.0.1:{server.server_port}/reports", queue=q, max_retries=3, backoff_base_s=0
            )
            ok = publisher.publish(self._sample_report())
            self.assertTrue(ok)
            self.assertEqual(len(_Handler.received), 3)
        finally:
            server.shutdown()
            server.server_close()


if __name__ == "__main__":
    unittest.main()
