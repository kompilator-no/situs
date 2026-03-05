# kotlin-spring-boot-sample-app

A Kotlin Spring Boot application demonstrating the **test-framework** library.

This is the Kotlin equivalent of `java-spring-boot-sample-app` — it shows that the
library works with Kotlin with no changes required to the library itself.

## What it includes

| File | Purpose |
|---|---|
| `KotlinSampleApplication.kt` | Spring Boot entry point with `@EnableRuntimeTests` |
| `Calculator.kt` | `@Service` bean used as subject under test |
| `CalculatorTestSuite.kt` | `@Component` + `@RuntimeTestSuite` — demonstrates Spring DI injection |
| `LongRunningTestSuite.kt` | Parallel suite demonstrating `timeoutMs` and `delayMs` |
| `RetryTestSuite.kt` | Suite demonstrating `retries` — flaky, always-fail, and stable tests |

## Kotlin-specific notes

### `kotlin.plugin.spring`
Applied in `build.gradle.kts` — automatically makes `@Component`-annotated classes `open`
so Spring can proxy them. Without this, Kotlin's default `final` classes would prevent proxying.

### `jackson-module-kotlin`
Added as a dependency — required for correct Jackson serialisation/deserialisation of Kotlin
classes in the API responses.

### `companion object` vs `static`
Kotlin uses `companion object` where Java uses `static`. The `flakyCallCount` in
`RetryTestSuite` is in a `companion object` so it persists across the fresh instances
the framework creates per retry attempt — exactly the same behaviour as the Java version.

### No `open` keyword needed
Thanks to `kotlin.plugin.spring`, `@Component` and `@RuntimeTestSuite` annotated classes
are automatically opened. You do **not** need to write `open class CalculatorTestSuite`.

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
```

## Run the app

```bash
./gradlew :kotlin-spring-boot-sample-app:bootRun
```

## Run tests

```bash
./gradlew :kotlin-spring-boot-sample-app:test
```
