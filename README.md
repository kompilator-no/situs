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
- `POST /api/runs` – start a run (returns `429` when queue is full). Supports optional `Idempotency-Key` header.
- `DELETE /api/runs/{runId}` – stop a run.
- `GET /api/runs/{runId}` – get status for a run.
- `GET /api/runs` – get status for all runs (optional query params: `testId`, `state`, `limit`, `offset`).
- `GET /api/runs/summary` – queue/runtime counters (`total`, `queued`, `running`, `completed`).
- `GET /api/tests` – list discovered test definitions (`runner:read`).
- `POST /api/runs` – start a run (returns `429` when queue is full) (`runner:execute`).
- `DELETE /api/runs/{runId}` – stop a run (`runner:cancel`).
- `GET /api/runs/{runId}` – get status for a run (`runner:read`).
- `GET /api/runs` – get status for all runs (optional query params: `testId`, `state`, `limit`, `offset`) (`runner:read`).
- `GET /api/runs/summary` – queue/runtime counters (`total`, `queued`, `running`, `completed`) (`runner:read`).


Idempotency behavior for `POST /api/runs`:

- Missing `Idempotency-Key`: unchanged behavior, each request creates a new run.
- Same key + same payload within `runner.idempotency-window`: returns `202` with the original `runId` and does not schedule a new run.
- Same key + different payload within window: returns `409 Conflict`.
- Same key after the window expires: treated as a new run request.

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

## Authentication and authorization

`/api/**` is protected with Spring Security as an OAuth2 resource server. Every API call requires a bearer token, and scopes are mapped directly to authorities:

- `runner:read` for read-only endpoints
- `runner:execute` for starting runs
- `runner:cancel` for cancelling runs

### JWT mode (default)

Set these environment variables:

- `RUNNER_JWT_ISSUER_URI`
- `RUNNER_JWT_AUDIENCE`

### Opaque token introspection mode

Enable opaque mode and configure introspection:

- `RUNNER_OPAQUE_ENABLED=true`
- `RUNNER_OPAQUE_INTROSPECTION_URI`
- `RUNNER_OPAQUE_CLIENT_ID`
- `RUNNER_OPAQUE_CLIENT_SECRET`

When opaque mode is enabled, introspection is used instead of JWT decoding.

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
- `RUNNER_IDEMPOTENCY_WINDOW`
- `RUNNER_DISCOVERY_PACKAGES`
- `RUNNER_JWT_ISSUER_URI`
- `RUNNER_JWT_AUDIENCE`
- `RUNNER_OPAQUE_ENABLED`
- `RUNNER_OPAQUE_INTROSPECTION_URI`
- `RUNNER_OPAQUE_CLIENT_ID`
- `RUNNER_OPAQUE_CLIENT_SECRET`


## Run history retention

`RunnerProperties` now supports history retention controls for completed runs:

- `historyTtl` (default: `PT24H`): terminal runs older than this are eligible for cleanup.
- `maxRunRecords` (default: `10000`): hard cap for terminal history; oldest completed runs are evicted first.
- `cleanupInterval` (default: `PT5M`, nullable): if set, periodic cleanup runs in the background; if `null`, cleanup is performed on write and list operations.

Operational tradeoffs:

- Lower TTL/cap values reduce memory usage but shorten audit/debug history.
- Background cleanup (`cleanupInterval`) smooths write latency but can do extra work on idle systems.
- On-write/on-read compaction keeps behavior deterministic without scheduler dependency but adds small overhead to hot paths.
- Active runs (`QUEUED`/`RUNNING`) are never evicted by retention cleanup.

Run summary counters now include cleanup metrics:

- `expiredDeleted`: how many TTL-expired terminal records were removed by the last compaction.
- `retainedCount`: number of terminal records kept after retention is applied.

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
