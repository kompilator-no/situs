# Current Status and Next Plan

## What has been done so far

1. Reviewed repository structure and identified active modules:
   - `framework-core`
   - `runner-service`
   - `reporting-client`
   - `transport-http`
   - `transport-kafka`
   - `transport-websocket`
2. Verified that the repository currently contains mostly build configuration and high-level documentation, with very limited implemented source code.
3. Reviewed the root `README.md` to confirm intended responsibilities for `runner-service` and `reporting-client`.

## Proposed plan for what to build next

### Phase 1 – Deliver a runnable minimum (MVP)

- Create baseline source layout in each module (`src/main`, `src/test`).
- Implement a minimal `runner-service` with:
  - health endpoint
  - `GET /api/tests`
  - `POST /api/runs`
  - in-memory run state
- Implement basic test discovery in `framework-core`.
- Add at least one end-to-end test that starts a run and reads status.

**Acceptance criteria:**
- `docker compose up --build` starts all required services without errors.
- At least one API flow is verifiable through tests or curl.

### Phase 2 – Harden the run engine

- Add queue/scheduler with configurable concurrency.
- Add timeout, retries, and cancellation handling to run lifecycle.
- Add persistence for run state (e.g., PostgreSQL or Redis) instead of only in-memory state.
- Add structured logging and correlation ID per run.

**Acceptance criteria:**
- Concurrent runs are handled deterministically.
- Timeout/retry/cancel behavior is covered by automated tests.

### Phase 3 – Result reporting

- Implement `reporting-client` with `ReportPayload v1`.
- Add idempotency key, retry/backoff, and fallback queue as described in README.
- Drain backlog before publishing a new report.
- Add contract tests against a simulated GUI backend.

**Acceptance criteria:**
- Temporary backend outages do not cause data loss.
- Duplicate publishing is avoided in normal failure scenarios.

### Phase 4 – Transport layer

- Define shared transport interface.
- Fully implement first transport (recommended: HTTP).
- Then add Kafka/WebSocket behind the same interface.
- Verify equivalent behavior across transports.

**Acceptance criteria:**
- The same test scenario runs over at least two transport types without application-level changes.

### Phase 5 – Quality and operations

- Add CI pipeline with build, test, lint, and optional security scanning.
- Add baseline observability: metrics, health/readiness, dashboards.
- Document operational procedures (local run, troubleshooting, rollback).
- Define versioning and release process.

**Acceptance criteria:**
- Changes are automatically validated in CI.
- The team can operate the service with documented routines.

## Recommended execution order

1. Phase 1 (MVP)
2. Phase 2 (run stability)
3. Phase 3 (robust reporting)
4. Phase 4 (multiple transports)
5. Phase 5 (operations maturity)

## Suggested first tasks (next sprint)

- [ ] Create `src/main` + `src/test` in all modules.
- [ ] Implement minimal `runner-service` API with in-memory storage.
- [ ] Add first integration test for run creation/status.
- [ ] Add simple CI job that runs build + test.
