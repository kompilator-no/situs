# transport-kafka

Kafka transport implementation of the shared transport contract.

## Supported transport interface

- **Protocol version:** `1.0`
- **Capabilities:**
  - `SYNC_REQUEST_RESPONSE`
  - `ERROR_DETAILS`
  - `ORDERING_GUARANTEE`

## Runtime behavior

`KafkaTransportClient` publishes each `TransportRequest` to Kafka and returns an acknowledgement-style response:

- Default topic: `test-framework-runs`
- Topic override: request header `topic`
- Key override: request header `key` (defaults to request id)
- Payload encoding header: `payload-encoding`
  - `utf-8` (default): sends payload string bytes as UTF-8
  - `base64`: decodes payload as Base64 and sends raw bytes (for binary/non-string data)
- Success response payload format: `kafka:<topic>:<partition>:<offset>`
- Validation failures: `KAFKA_VALIDATION_ERROR` (non-retriable)
- Invalid encoding/payload decoding: `KAFKA_INVALID_ENCODING` (non-retriable)
- Broker/publish failures: `KAFKA_SEND_FAILED` (retriable)

- Producer config overrides: pass `producerConfigOverrides` to `KafkaTransportClient` to override defaults (for example `acks`, batching/compression, security settings).

## Usage

```kotlin
val client = KafkaTransportClient(
    bootstrapServers = "localhost:9092",
    defaultTopic = "test-runs",
    producerConfigOverrides = mapOf(
        "acks" to "1",
        "compression.type" to "gzip",
    ),
)

// UTF-8 text payload (default)
client.execute(
    TransportRequest(id = "run-1", payload = "hello world")
)

// Binary payload via base64
client.execute(
    TransportRequest(
        id = "run-2",
        payload = "AQIDfw==",
        headers = mapOf("payload-encoding" to "base64"),
    ),
)
```

## Migration guidance

- Keep Kafka request/response envelope compatible with the shared transport contract for protocol `1.0`.
- If changing ordering semantics or error shape, introduce a new protocol version and coordinate support in `framework-core` startup checks.
- Validate every change by running `./gradlew :transport-kafka:test` to ensure conformance.
