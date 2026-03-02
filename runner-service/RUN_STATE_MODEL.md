# Runner service run-state model

## States

- `QUEUED`: Run has been accepted and is waiting for execution capacity.
- `RUNNING`: Run is currently executing.
- `PASSED`: Run completed successfully.
- `FAILED`: Run completed with unrecoverable failure.
- `CANCELLED`: Run was intentionally stopped before completion.

Terminal states are `PASSED`, `FAILED`, and `CANCELLED`.

## Allowed transitions

- `QUEUED -> RUNNING`
- `QUEUED -> CANCELLED`
- `RUNNING -> PASSED`
- `RUNNING -> FAILED`
- `RUNNING -> CANCELLED`

No transitions are allowed out of terminal states.

## Retention behavior

- Only terminal records are subject to retention cleanup.
- Terminal runs older than `historyTtl` are expired.
- If terminal records exceed `maxRunRecords`, oldest terminal records by `finishedAt` are evicted first.
- Active runs (`QUEUED`, `RUNNING`) are never evicted by retention logic.
