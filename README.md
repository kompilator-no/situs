# reporting-client

Et lite klientbibliotek for versjonert publisering av testresultater mot GUI-backend via HTTP.

## Skjema (v1)

`ReportPayload` består av:

- `schema_version`: versjon av resultatformat (`v1`).
- `metadata`:
  - `run_id`
  - `suite`
  - `environment`
  - `commit_id`
  - `build_id`
  - `started_at`
  - `finished_at`
- `tests[]`:
  - testnivå: `test_id`, `name`, `status`, `duration_ms`, `error_message`, `attachments[]`
  - stegnivå (`steps[]`): `name`, `status`, `duration_ms`, `error_message`, `attachments[]`
- `summary`: aggregert `passed`, `failed`, `skipped`

## Robust publisering

`ReportPublisher` sender payload via HTTP `POST` med:

- `Idempotency-Key`: deterministisk nøkkel av `run_id + sha256(payload)`.
- Retry/backoff med eksponentiell ventetid + jitter ved:
  - HTTP `429`
  - HTTP `5xx`
  - nettverksfeil (`URLError`, timeout)
- Fallback-kø (`FileBackedQueue`) i JSONL-format ved vedvarende feil.
- Drenering av kø før ny rapport, for å sende backlog i rekkefølge.

## Bruk

```python
from reporting_client import TestResult, StepResult, new_report, ReportPublisher

report = new_report(
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
        )
    ],
)

publisher = ReportPublisher("http://gui-backend.local/api/reports")
publisher.publish(report)
```
