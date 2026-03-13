# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project follows Semantic Versioning.

## [Unreleased]

## [3.0.1] - 2026-03-10

### Changed
- License changed from MIT to Apache-2.0
- Published POM license metadata updated to Apache License 2.0
- Published SCM/project URLs updated from `kompilator-no/test-framework` to `kompilator-no/situs`

## [3.0.0] - 2026-03-10

### Breaking
- Java package namespace renamed from `no.kompilator.testframework...` to `no.kompilator.situs...`
- Plugin package namespace renamed from `no.kompilator.testframework.plugins...` to `no.kompilator.situs.plugins...`
- Core artifact and module renamed from `java-library` to `situs`
- Consumers must update imports, Gradle/Maven coordinates, and any reflection-based or Spring class-name references that pointed at the old namespaces

### Added
- Async run progress with per-test timestamps and explicit progress counters
- Per-test plugin events via `SuiteRunListener#onTestCompleted(...)`
- Run cancellation support through `TestFrameworkService#cancelRun(...)` and `POST /api/test-framework/runs/{runId}/cancel`
- Package-scoped Spring discovery configuration and bounded run retention
- Maven Central publishing and GitHub release workflows

### Changed
- Project branding and documentation now describe Situs as a system integration testing library
- Reporting plugin now observes run results instead of controlling execution
- Spring integration is split from framework-agnostic service and model code
- Repository remote moved to `kompilator-no/situs`

### Fixed
- Single-test execution now runs only the selected test
- Async and parallel executor leaks
- Empty parallel suite handling
- Duplicate suite and test validation at startup
- Sample app and release documentation consistency

### License
- License changed from MIT to Apache-2.0
