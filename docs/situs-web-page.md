# Situs

Situs is an annotation-driven system integration testing library for Java and Kotlin. It lets you define runtime test suites as plain classes, execute them on demand through code or HTTP, and publish structured results in formats such as JUnit XML, Open Test Reporting XML, and JSON.

Unlike a build-time unit test framework, Situs is designed for production-like environments where you want to verify real integrations, real infrastructure, and real application wiring while the system is running.

## Why Situs

- Run system integration test suites at runtime
- Discover suites automatically through package scanning
- Use Spring dependency injection in test suites
- Execute suites sequentially or in parallel
- Configure retries, delays, and timeouts per test
- Use parameterized tests with generated invocation names
- Trigger runs through a built-in HTTP API
- Export reports for CI systems and external tooling

## Core concepts

### `@TestSuite`

Marks a class as a discoverable runtime test suite.

```java
@TestSuite(name = "PaymentTestSuite", description = "Checks end-to-end payment flows")
public class PaymentTestSuite {
}
```

### `@Test`

Marks a regular runtime test method.

```java
@Test(name = "chargeSucceeds", timeout = "PT30S")
public void chargeSucceeds() {
    assertThat(paymentService.charge(100)).isTrue();
}
```

### `@ParameterizedTest`

Marks a parameterized runtime test. Each resolved argument set becomes a separate logical test case in discovery, reporting, and HTTP responses.

```java
@ParameterizedTest(name = "add[{index}] {0}+{1}={2}")
@CsvSource({"1,2,3", "20,22,42"})
public void additionCases(int left, int right, int expected) {
    assertThat(calculator.add(left, right)).isEqualTo(expected);
}
```

Supported parameter sources:

- `@ValueSource`
- `@CsvSource`
- `@MethodSource`
- `@EnumSource`
- `@NullSource`
- `@EmptySource`
- `@NullAndEmptySource`

## Quick start

### Gradle

```kotlin
dependencies {
    implementation("no.kompilator:situs:3.1.0")
    implementation("no.kompilator:plugins:3.1.0")
}
```

### Java example

```java
import no.kompilator.situs.annotations.CsvSource;
import no.kompilator.situs.annotations.ParameterizedTest;
import no.kompilator.situs.annotations.Test;
import no.kompilator.situs.annotations.TestSuite;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;

@Component
@TestSuite(name = "CalculatorTestSuite", description = "Tests for Calculator")
public class CalculatorTestSuite {

    private final Calculator calculator;

    public CalculatorTestSuite(Calculator calculator) {
        this.calculator = calculator;
    }

    @Test(name = "divisionByZero", timeout = "PT0.5S")
    public void divisionByZero() {
        assertThat(calculator.divide(20, 4)).isEqualTo(5);
    }

    @ParameterizedTest(name = "add[{index}] {0}+{1}={2}")
    @CsvSource({"1,2,3", "2,3,5", "40,2,42"})
    public void additionCases(int left, int right, int expected) {
        assertThat(calculator.add(left, right)).isEqualTo(expected);
    }
}
```

### Spring Boot discovery

```properties
testframework.scan-packages=com.example.tests
testframework.full-classpath-scan=false
testframework.max-stored-runs=200
testframework.reporting.enabled=true
testframework.reporting.output-dir=build/test-reports
testframework.reporting.formats=JUNIT_XML,OPEN_TEST_REPORTING_XML,JSON
```

When Situs is on the Spring Boot classpath, the runtime test API is auto-configured. In non-Boot Spring applications, use `@EnableRuntimeTests` for explicit opt-in.

## Parameterized tests

Invocation names are controlled with the `name` template on `@ParameterizedTest`.

Supported placeholders:

- `{index}` for the 1-based invocation index
- `{arguments}` for the full rendered argument list
- `{0}`, `{1}`, ... for individual argument values

Example:

```java
@ParameterizedTest(name = "blank[{index}]={0}")
@NullAndEmptySource
@ValueSource(strings = {" ", "\t"})
public void rejectsBlankInput(String value) {
    assertThat(value == null || value.isBlank()).isTrue();
}
```

For multi-parameter `@MethodSource`, emit `Arguments.of(...)`:

```java
@ParameterizedTest(name = "multiply[{index}] {0}*{1}={2}")
@MethodSource("cases")
public void multiplies(int left, int right, int expected) {
    assertThat(left * right).isEqualTo(expected);
}

static Stream<Arguments> cases() {
    return Stream.of(
            Arguments.of(2, 3, 6),
            Arguments.of(7, 6, 42));
}
```

## Timeout configuration

Situs supports two timeout styles:

- `timeoutMs = 500`
- `timeout = "PT30S"`

The `timeout` form uses ISO-8601 `java.time.Duration` syntax.

Examples:

- `PT0.5S` = 500 milliseconds
- `PT30S` = 30 seconds
- `PT5M` = 5 minutes
- `PT1H` = 1 hour

Rules:

- `timeout` and `timeoutMs` are mutually exclusive
- blank `timeout` means "not set"
- `timeoutMs = -1` disables timeout enforcement
- invalid duration strings fail fast during suite registration

## Execution model

- `@BeforeAll` runs once before the suite
- `@BeforeEach` runs before each test or parameterized invocation
- `@AfterEach` runs after each test or parameterized invocation
- `@AfterAll` runs once after the suite
- `@TestSuite(parallel = true)` enables parallel execution
- Retries re-run the full per-test lifecycle

## HTTP API

Typical endpoints:

```bash
# Health check
curl http://localhost:8080/api/test-framework/status

# List discovered suites
curl http://localhost:8080/api/test-framework/suites

# Start a suite
curl -X POST http://localhost:8080/api/test-framework/suites/CalculatorTestSuite/run

# Run a single generated invocation
curl -X POST http://localhost:8080/api/test-framework/suites/ParameterizedCalculatorTestSuite/tests/add[1]%201+2=3/run

# Poll run status
curl http://localhost:8080/api/test-framework/runs/{runId}/status

# Cancel a running run
curl -X POST http://localhost:8080/api/test-framework/runs/{runId}/cancel
```

## Reporting

With the `plugins` artifact, Situs can write:

- JUnit XML
- Open Test Reporting XML
- JSON

Reports are generated after suite runs and can be consumed by CI pipelines, dashboards, or custom tools.

## Kotlin support

Situs works with Kotlin out of the box. Use `kotlin("plugin.spring")` so `@Component` classes are opened for Spring proxying. `@MethodSource` providers can be placed in a `companion object` and exposed with `@JvmStatic`.

## Supported public API

Build against these packages:

- `no.kompilator.situs.annotations`
- `no.kompilator.situs.model`
- `no.kompilator.situs.params`
- `no.kompilator.situs.plugin`
- `no.kompilator.situs.service`
- `no.kompilator.situs.spring`
- `no.kompilator.situs.spring.model`

## Sample applications

This repository includes:

- `java-spring-boot-sample-app`
- `kotlin-spring-boot-sample-app`

Both demonstrate runtime execution, Spring DI, parameterized tests, retries, and timeout handling.
