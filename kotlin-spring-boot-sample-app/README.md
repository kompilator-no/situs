# kotlin-spring-boot-sample-app

A Kotlin Spring Boot application demonstrating the **situs** library.

This is the Kotlin equivalent of `java-spring-boot-sample-app` — it shows that the
library works with Kotlin with no changes required to the library itself.

## What it includes

| File | Purpose |
|---|---|
| `KotlinSampleApplication.kt` | Spring Boot entry point — framework auto-config activates from the classpath |
| `Calculator.kt` | `@Service` bean used as subject under test |
| `CalculatorTestSuite.kt` | `@Component` + `@TestSuite` — demonstrates Spring DI injection |
| `ParameterizedCalculatorTestSuite.kt` | Parameterized suite demonstrating `@CsvSource` and `@MethodSource` |
| `LongRunningTestSuite.kt` | Parallel suite demonstrating `timeoutMs`, ISO-8601 `timeout`, and `delayMs` |
| `RetryTestSuite.kt` | Suite demonstrating `retries` — flaky, always-fail, and stable tests |

## Discovery configuration

This sample scopes suite discovery to:

```properties
testframework.scan-packages=no.kompilator.kotlinapp.tests
```

The reporting plugin writes reports for suite runs, but it does not auto-run suites on startup.

## Parameterized tests

This sample includes `ParameterizedCalculatorTestSuite.kt`, which shows both:

- `@CsvSource` for inline multi-argument cases
- `@MethodSource` with `Arguments.of(...)` emitted from a `companion object` `@JvmStatic` provider

Example:

```kotlin
@ParameterizedTest(name = "add[{index}] {0}+{1}={2}")
@CsvSource("1,2,3", "20,22,42")
fun additionCases(left: Int, right: Int, expected: Int) {
    assertThat(calculator.add(left, right)).isEqualTo(expected)
}
```

Generated invocation names are exposed through the HTTP API just like regular tests.

## Kotlin-specific notes

### `kotlin.plugin.spring`
Applied in `build.gradle.kts` — automatically makes `@Component`-annotated classes `open`
so Spring can proxy them. Without this, Kotlin's default `final` classes would prevent proxying.

### `companion object` vs `static`
Kotlin uses `companion object` where Java uses `static`. The `flakyCallCount` in
`RetryTestSuite` is in a `companion object` so it persists across the fresh instances
the framework creates per retry attempt — exactly the same behaviour as the Java version.
The same pattern is used for `@MethodSource` providers with `@JvmStatic`.

### No `open` keyword needed
Thanks to `kotlin.plugin.spring`, `@Component` and `@TestSuite` annotated classes
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

# Cancel a running suite
curl -X POST http://localhost:8080/api/test-framework/runs/{runId}/cancel
```

## Run the app

```bash
./situs/gradlew :kotlin-spring-boot-sample-app:bootRun
```

## Run tests

```bash
./situs/gradlew :kotlin-spring-boot-sample-app:test
```
