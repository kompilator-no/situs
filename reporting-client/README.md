# reporting-client

`reporting-client` publishes run results to an external reporting backend with reliability semantics:

- payload schema `v1`
- idempotency key support
- transient retry with exponential backoff + jitter
- fallback backlog queue
- backlog drain before new publish

## Basic usage

```kotlin
val client = ReportingClient(
    config = ReportingClientConfig(endpoint = "https://reporting.example.com/api/reports")
)

val result = client.publish(payload)
```

## Configuration

- `endpoint`: target report endpoint.
- `maxRetries`: retry count for transient failures (429/5xx/network exceptions).
- `initialBackoff`: first retry delay.
- `maxBackoff`: upper bound for exponential backoff.
- `jitterRatio`: randomized delay factor (`0.0` to `1.0`).
- `maxDrainPerPublish`: max queued items drained before current payload publish.

## Backlog stores

- `InMemoryBacklogStore`: default in-memory FIFO queue.
- `FileBacklogStore`: JSON-backed queue persisted to disk.

## Delivery behavior

- `2xx`: delivered.
- `409`: treated as already delivered (idempotent duplicate).
- `429` / `5xx` / network exception: retried; if retries exhaust, payload is enqueued.
- other `4xx`: permanent failure (not enqueued).
