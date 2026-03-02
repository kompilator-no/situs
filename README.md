# runner-service

Spring Boot-tjeneste som oppdager testdefinisjoner, eksponerer API for kjøringer, og håndterer kø/scheduler med retries, timeout og avbrudd.

## API

- `GET /api/tests` – list oppdagede testdefinisjoner.
- `POST /api/runs` – start kjøring.
- `DELETE /api/runs/{runId}` – stopp kjøring.
- `GET /api/runs/{runId}` – status for en kjøring.
- `GET /api/runs` – status for alle kjøringer.

Eksempel `POST /api/runs`:

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

## Konfigurasjon

Miljø kan styres via `application.yaml` eller miljøvariabler:

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

Bygg og kjør:

```bash
docker compose up --build
```
