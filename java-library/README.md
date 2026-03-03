# Java Library (MVP)

This module provides initial Java domain objects for defining and sharing test-framework test suites.

## Java version

The library is configured to use Java 21.

## Domain objects

- `TestSuite`: top-level suite metadata and ordered steps.
- `TestStep`: a single test step with actions and validators.
- `TestAction`: a generic action descriptor (`type`, `target`, and dynamic parameters).
- `TestValidator`: a generic validator descriptor (`type`, `target`, and expected values).

## Build

From repository root:

```bash
./java-library/gradlew -p java-library build
```
