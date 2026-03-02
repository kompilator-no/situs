# transport-kafka

## Supported transport interface

- **Protocol version:** `1.0`
- **Capabilities:**
  - `SYNC_REQUEST_RESPONSE`
  - `ERROR_DETAILS`
  - `ORDERING_GUARANTEE`

## Migration guidance

- Keep Kafka request/response envelope compatible with the shared transport contract for protocol `1.0`.
- If changing ordering semantics or error shape, introduce a new protocol version and coordinate support in `framework-core` startup checks.
- Validate every change by running `./gradlew :transport-kafka:test` to ensure conformance.
