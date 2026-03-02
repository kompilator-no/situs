# runner-service

Spring Boot service that discovers test definitions, exposes execution APIs, and handles queue/scheduler behavior with retries, timeouts, and cancellation.

## Structure

- `no.testframework.runnerlib.*`: reusable library containing all core logic for:
  - test discovery
  - execution/scheduling
  - API DTOs and controller
  - configuration and run model
- `sample-app/`: sample Spring Boot application that uses the runner library:
  - `sample-app/src/main/java/no/testframework/sampleapp/SampleRunnerApplication.java` (entrypoint)
  - `sample-app/src/main/java/no/testframework/sampleapp/SmokeTestDefinition.java` (sample registered test)

## API

- `GET /api/tests` – list discovered test definitions.
- `POST /api/runs` – start a run (returns `429` when queue is full).
- `DELETE /api/runs/{runId}` – stop a run.
- `GET /api/runs/{runId}` – get status for a run.
- `GET /api/runs` – get status for all runs (optional query params: `testId`, `state`, `limit`, `offset`).
- `GET /api/runs/summary` – queue/runtime counters (`total`, `queued`, `running`, `completed`).

Example `POST /api/runs`:

```json
{
  "testId": "smoke-test",
  "retries": 2,
  "timeout": "PT30S",
  "context": {
    "delayMs": 100
  }
}
```

## Configuration

Environment can be controlled via `sample-app/src/main/resources/application.yaml` or environment variables:

- `KAFKA_BROKERS`
- `ORCHESTRATOR_BASE_URL`
- `TESTDATA_BASE_URL`
- `AUTH_CLIENT_ID`
- `AUTH_CLIENT_SECRET`
- `AUTH_TOKEN_URL`
- `RUNNER_CONCURRENCY`
- `RUNNER_QUEUE_CAPACITY`
- `RUNNER_DEFAULT_TIMEOUT`
- `RUNNER_DEFAULT_RETRIES`
- `RUNNER_DISCOVERY_PACKAGES`

## Container

Build and run:

```bash
docker compose up --build
```

# reporting-client

A small client library for versioned publishing of test results to a GUI backend over HTTP.

## Schema (v1)

`ReportPayload` contains:

- `schema_version`: result format version (`v1`).
- `metadata`:
  - `run_id`
  - `suite`
  - `environment`
  - `commit_id`
  - `build_id`
  - `started_at`
  - `finished_at`
- `tests[]`:
  - test level: `test_id`, `name`, `status`, `duration_ms`, `error_message`, `attachments[]`
  - step level (`steps[]`): `name`, `status`, `duration_ms`, `error_message`, `attachments[]`
- `summary`: aggregated `passed`, `failed`, `skipped`

## Reliable publishing

`ReportPublisher` sends payload via HTTP `POST` with:

- `Idempotency-Key`: deterministic key from `run_id + sha256(payload)`.
- Retry/backoff with exponential delay + jitter on:
  - HTTP `429`
  - HTTP `5xx`
  - network errors (`URLError`, timeout)
- Fallback queue (`FileBackedQueue`) in JSONL format on persistent failures.
- Queue draining before sending a new report, so backlog is delivered in order.

## Usage

See the Kotlin modules (`framework-core`, `runner-service`, `reporting-client`) for additional usage examples.


## Observability quickstart

The sample app emits run lifecycle logs as JSON with the schema:
`runId`, `testId`, `state`, `attempt`, `correlationId`, `durationMs`, `errorType`.

It also propagates `X-Correlation-Id` and OpenTelemetry trace identifiers through run context,
transport helper modules, and reporting client calls.

### Required OpenTelemetry exporter config

Set these environment variables before starting the app:

```bash
export OTEL_SERVICE_NAME=runner-service
export OTEL_TRACES_EXPORTER=otlp
export OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
```

Optional for local debugging:

```bash
export OTEL_LOG_LEVEL=debug
```

Then start the sample app and call `POST /api/runs` with a correlation id:

```bash
curl -X POST http://localhost:8080/api/runs \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-correlation-1' \
  -d '{"testId":"smoke-test"}'
```
