# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project follows Semantic Versioning.

## [Unreleased]

## [2.0.0] - 2026-03-09

### Breaking
- Java package namespace renamed from `no.kompilator.testframework...` to `no.kompilator.situs...`
- Plugin package namespace renamed from `no.kompilator.testframework.plugins...` to `no.kompilator.situs.plugins...`
- Consumers must update imports, reflection-based references, and any Spring class-name references that pointed at the old packages

### Added
- Async run progress with per-test timestamps and explicit progress counters
- Per-test plugin events via `SuiteRunListener#onTestCompleted(...)`
- Run cancellation support through `TestFrameworkService#cancelRun(...)` and `POST /api/test-framework/runs/{runId}/cancel`
- Package-scoped Spring discovery configuration and bounded run retention
- Maven Central publishing and GitHub release workflows

### Changed
- Core artifact/module renamed from `java-library` to `situs`
- Reporting plugin now observes run results instead of controlling execution
- Spring integration split from framework-agnostic service/model code

### Fixed
- Single-test execution now runs only the selected test
- Async and parallel executor leaks
- Empty parallel suite handling
- Duplicate suite/test validation at startup
- Sample app and release documentation consistency
