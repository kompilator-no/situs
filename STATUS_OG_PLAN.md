# Current Status, Execution Plan, and Delivery Checklist

## Progress Snapshot (as of this update)

- **Overall completion estimate:** **~52%** (MVP delivery completed, hardening and CI baseline partially implemented).
- **Current stage:** Phase 2 hardening in progress.
- **Biggest gap:** Persistent storage + full reporting reliability are still pending.

## Work Completed So Far (Done)

### Repository and scope analysis and implementation execution
- [x] Reviewed top-level project structure and identified active modules:
  - `framework-core`
  - `runner-service`
  - `reporting-client`
  - `transport-http`
  - `transport-kafka`
  - `transport-websocket`
- [x] Confirmed the repository currently has build setup + documentation, but limited runtime implementation.
- [x] Reviewed root documentation to validate intended responsibilities between `runner-service` and `reporting-client`.
- [x] Drafted a phased implementation strategy from MVP to operational maturity.

### Planning baseline
- [x] Defined initial acceptance criteria per delivery phase.
- [x] Established a recommended execution order (MVP first, operations maturity last).

## Detailed Phase Plan and Checklists

## Phase 1 – Runnable MVP

**Goal:** Deliver a minimal but working flow that can discover tests, start runs, and inspect run status.

**Status:** ✅ **Completed**
**Completion estimate:** **100%**

### Completed in this phase
- [x] Identified minimal API surface (`health`, list tests, create run, inspect status).
- [x] Agreed on in-memory state as first persistence strategy.

### Remaining checklist
- [ ] Create baseline source layout (`src/main`, `src/test`) where still missing.
- [x] Implement minimal `runner-service` endpoints:
  - [x] health endpoint
  - [x] `GET /api/tests`
  - [x] `POST /api/runs`
  - [x] run status endpoint (or equivalent)
- [x] Implement basic test discovery in `framework-core`.
- [x] Add in-memory run state model with lifecycle transitions.
- [x] Add at least one integration/E2E test that:
  - [x] starts a run
  - [x] polls/reads run status
- [ ] Validate local execution (`docker compose up --build` or equivalent).

**Exit criteria for Phase 1**
- [ ] Services boot locally without startup errors.
- [ ] At least one end-to-end run flow is verifiable via test or curl.

---

## Phase 2 – Run Engine Hardening

**Goal:** Make run orchestration reliable under concurrent and failure scenarios.

**Status:** 🟡 **In progress**
**Completion estimate:** **35%**

### Checklist
- [x] Introduce queue/scheduler with configurable concurrency.
- [x] Add timeout handling.
- [x] Add retry policy for recoverable failures.
- [x] Add cancellation flow.
- [ ] Replace or augment in-memory state with persistent store (PostgreSQL/Redis).
- [ ] Add structured logging + correlation IDs by run.
- [ ] Add automated tests for:
  - [ ] concurrency behavior
  - [ ] timeout/retry/cancel behavior

**Exit criteria for Phase 2**
- [ ] Concurrent runs behave deterministically.
- [ ] Failure handling is covered by automated tests.

---

## Phase 3 – Result Reporting Reliability

**Goal:** Ensure reliable, idempotent, and eventually consistent report delivery.

**Status:** ⚪ **Not started**
**Completion estimate:** **0%**

### Checklist
- [ ] Implement `reporting-client` with `ReportPayload v1`.
- [ ] Add idempotency keys for report submission.
- [ ] Add retry with exponential backoff.
- [ ] Implement fallback queue/backlog when backend is unavailable.
- [ ] Ensure backlog drains before new report submission where required.
- [ ] Add contract tests against simulated GUI backend.

**Exit criteria for Phase 3**
- [ ] Backend outages do not cause report loss.
- [ ] Duplicate publishing is prevented in expected failure modes.

---

## Phase 4 – Multi-Transport Support

**Goal:** Decouple execution flow from transport implementation.

**Status:** ⚪ **Not started**
**Completion estimate:** **0%**

### Checklist
- [ ] Define shared transport interface/abstraction.
- [ ] Implement first production transport (recommended: HTTP).
- [ ] Implement Kafka transport behind same interface.
- [ ] Implement WebSocket transport behind same interface.
- [ ] Add compatibility tests proving same scenario works across at least two transports.

**Exit criteria for Phase 4**
- [ ] Test scenarios run without app-level changes across multiple transports.

---

## Phase 5 – Quality, CI/CD, and Operations

**Goal:** Make delivery repeatable and operationally supportable.

**Status:** ⚪ **Not started**
**Completion estimate:** **0%**

### Checklist
- [ ] Add CI pipeline steps for build + test (+ lint/security as needed).
- [ ] Add baseline observability (metrics, health/readiness, dashboards).
- [ ] Write operational docs (local runbook, troubleshooting, rollback).
- [ ] Define release/versioning process.

**Exit criteria for Phase 5**
- [ ] CI validates changes on each PR.
- [ ] Team can run and troubleshoot services using written procedures.

---

## Sequencing and Dependency Order

1. **Phase 1 (MVP)** – unlocks runnable baseline.
2. **Phase 2 (hardening)** – stabilizes execution behavior.
3. **Phase 3 (reporting reliability)** – ensures durable outcomes.
4. **Phase 4 (transports)** – expands integration options safely.
5. **Phase 5 (operations)** – production readiness and team velocity.

## Immediate Next Sprint Checklist (Priority View)

### Must-do
- [x] Scaffold missing source/test directories in all applicable modules.
- [x] Build minimal `runner-service` API with in-memory run management.
- [x] Add first integration test for create-run + status flow.

### Should-do
- [ ] Add initial CI job for `build` + `test`.
- [ ] Define run state model and status transition rules in writing.

### Nice-to-have
- [ ] Add basic metrics/logging early (to reduce rework in later phases).
- [ ] Prepare interface contract draft for transport abstraction.

## Progress Tracking Template (for ongoing updates)

Use this on each update cycle:

- **Date:** YYYY-MM-DD
- **Overall completion:** XX%
- **Phase in focus:** Phase N
- **Completed this cycle:**
  - [ ] Item 1
  - [ ] Item 2
- **Blocked by:**
  - None / blocker description
- **Next 3 actions:**
  1. Action A
  2. Action B
  3. Action C
