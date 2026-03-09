# java-spring-boot-sample-app

A Java Spring Boot application demonstrating the **test-framework** library.

## What it includes

| Class | Purpose |
|---|---|
| `SampleAppApplication` | Spring Boot entry point — framework auto-config activates from the classpath |
| `Calculator` | `@Service` bean used as subject under test |
| `CalculatorTestSuite` | `@Component` + `@TestSuite` — demonstrates Spring DI injection |
| `LongRunningTestSuite` | Parallel suite demonstrating `timeoutMs` and `delayMs` |
| `RetryTestSuite` | Suite demonstrating `retries` — flaky, always-fail, and stable tests |

## Features demonstrated

- **Auto-discovery** — suites are found by package-scoped scanning via `testframework.scan-packages`
- **Spring DI** — `CalculatorTestSuite` receives `Calculator` via constructor injection
- **Parallel execution** — `LongRunningTestSuite` runs all tests concurrently
- **Timeouts** — `timeoutMs` cancels tests that run too long
- **Delays** — `delayMs` waits before starting a test
- **Retries** — `retries = 2` re-runs a failing test up to 3 times total

## Configuration

This sample scopes suite discovery to:

```properties
testframework.scan-packages=no.kompilator.sampleapp.tests
```

The reporting plugin writes reports for suite runs, but it does not auto-run suites on startup.

## HTTP API

```bash
# Health check
curl http://localhost:8080/api/test-framework/status

# List all discovered suites
curl http://localhost:8080/api/test-framework/suites

# Start a suite run
curl -X POST http://localhost:8080/api/test-framework/suites/CalculatorTestSuite/run

# Poll run status
curl http://localhost:8080/api/test-framework/runs/{runId}/status

# Cancel a running suite
curl -X POST http://localhost:8080/api/test-framework/runs/{runId}/cancel
```

## Run the app

```bash
./java-library/gradlew :java-spring-boot-sample-app:bootRun
```

## Run tests

```bash
./java-library/gradlew :java-spring-boot-sample-app:test
```
