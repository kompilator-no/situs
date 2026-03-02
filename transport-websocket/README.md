# transport-websocket

## Supported transport interface

- **Protocol version:** `1.0`
- **Capabilities:**
  - `SYNC_REQUEST_RESPONSE`
  - `ERROR_DETAILS`
  - `BIDIRECTIONAL_STREAM`

## Migration guidance

- Preserve the `1.0` contract for request ID echoing and structured errors when evolving frame handling.
- Any incompatible frame/protocol change requires a protocol version bump and an update to compatibility checks in `framework-core`.
- Validate every change by running `./gradlew :transport-websocket:test` to ensure conformance.
