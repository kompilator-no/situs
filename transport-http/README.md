# transport-http

## Supported transport interface

- **Protocol version:** `1.0`
- **Capabilities:**
  - `SYNC_REQUEST_RESPONSE`
  - `ERROR_DETAILS`

## Migration guidance

- Minor behavior additions must keep protocol version `1.0` and preserve request/response/error semantics from the shared contract suite.
- Breaking changes (e.g., new mandatory fields or changed error contract) must bump protocol version and update `TransportCompatibility` support in `framework-core` before rollout.
- Validate every change by running `./gradlew :transport-http:test` to ensure conformance.
