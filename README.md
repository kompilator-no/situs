# situs

[![Maven Central - situs](https://img.shields.io/maven-central/v/no.kompilator/situs?label=situs)](https://central.sonatype.com/artifact/no.kompilator/situs)
[![Maven Central - plugins](https://img.shields.io/maven-central/v/no.kompilator/plugins?label=plugins)](https://central.sonatype.com/artifact/no.kompilator/plugins)

An annotation-driven system integration testing library for Java 21 under the `no.kompilator.situs` namespace. Define SIT suites as plain Java classes, run them on demand via a REST API or programmatically, and get structured reports in JUnit XML, Open Test Reporting XML, or JSON.

Like JUnit, but aimed at System Integration Testing (SIT): suites run **at runtime in production-like environments** instead of only at build time. They support Spring dependency injection, parallel execution, timeouts, delays, retries, and deterministic ordering.

---

## Repository structure

```
.
├── situs/                   Core library — annotations, engine, Spring integration
├── plugins/                        Ready-made plugins (reporting: JUnit XML, OTR XML, JSON)
├── java-spring-boot-sample-app/    Java Spring Boot example using the library
└── kotlin-spring-boot-sample-app/  Kotlin Spring Boot example using the library
```

| Module | Artifact | Description |
|---|---|---|
| `situs` | `no.kompilator:situs` | SIT annotations, execution engine, and HTTP API |
| `plugins` | `no.kompilator:plugins` | Reporting plugin — writes structured test reports |
| `java-spring-boot-sample-app` | — | Java sample app (not published) |
| `kotlin-spring-boot-sample-app` | — | Kotlin sample app (not published) |

Published artifacts:

- [`no.kompilator:situs`](https://central.sonatype.com/artifact/no.kompilator/situs)
- [`no.kompilator:plugins`](https://central.sonatype.com/artifact/no.kompilator/plugins)

---

## Supported API Surface

Supported packages:

- `no.kompilator.situs.annotations`
- `no.kompilator.situs.model`
- `no.kompilator.situs.params`
- `no.kompilator.situs.plugin`
- `no.kompilator.situs.service`
- `no.kompilator.situs.spring`
- `no.kompilator.situs.spring.model`

Internal packages that may change without notice:

- `no.kompilator.situs.domain`
- `no.kompilator.situs.runtime`

Build against the supported packages only.

---

## Quick start

### 1. Add the dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("no.kompilator:situs:2.0.0")

    // Optional — adds structured report writing (pulls in situs transitively)
    implementation("no.kompilator:plugins:2.0.0")
}
```

### 2. Define a test suite

```java
@TestSuite(name = "CalculatorTestSuite", description = "Tests for Calculator")
public class CalculatorTestSuite {

    @Test(name = "addition", description = "2 + 3 should equal 5", order = 1)
    public void testAddition() {
        assertThat(2 + 3).isEqualTo(5);
    }

    @Test(name = "divisionByZero", timeout = "PT0.5S", order = 2)
    public void testDivisionByZero() {
        assertThatThrownBy(() -> 1 / 0)
                .isInstanceOf(ArithmeticException.class);
    }

    @ParameterizedTest(name = "addition[{index}] {0}+{1}={2}")
    @CsvSource({"1,2,3", "2,3,5", "40,2,42"})
    public void testAdditionCases(int left, int right, int expected) {
        assertThat(left + right).isEqualTo(expected);
    }
}
```

### 3. Spring Boot — package-scoped discovery

```java
@SpringBootApplication
public class MyApp {
    public static void main(String[] args) {
        SpringApplication.run(MyApp.class, args);
    }
}
```

```properties
testframework.scan-packages=com.example.tests
```

Spring Boot auto-configuration is enabled automatically when the library is on the classpath. `@EnableRuntimeTests` is only needed for explicit opt-in in non-Boot Spring applications.

Useful Spring properties:

```properties
testframework.scan-packages=com.example.tests
testframework.full-classpath-scan=false
testframework.max-stored-runs=200
testframework.reporting.enabled=true
testframework.reporting.output-dir=build/test-reports
testframework.reporting.formats=JUNIT_XML,OPEN_TEST_REPORTING_XML,JSON
```

### 4. Run tests via HTTP

```bash
# List all discovered suites
curl http://localhost:8080/api/test-framework/suites

# Start a suite run (async)
curl -X POST http://localhost:8080/api/test-framework/suites/CalculatorTestSuite/run
# → {"runId":"abc-123"}

# Poll until COMPLETED
curl http://localhost:8080/api/test-framework/runs/abc-123/status

# Cancel a running suite
curl -X POST http://localhost:8080/api/test-framework/runs/abc-123/cancel
```

---

## Key features

| Feature | Annotation / API |
|---|---|
| Define a test suite | `@TestSuite` |
| Define a test method | `@Test` |
| Define parameterized tests | `@ParameterizedTest` with `@ValueSource`, `@CsvSource`, `@MethodSource`, `@EnumSource` |
| Setup / teardown | `@BeforeAll`, `@AfterAll`, `@BeforeEach`, `@AfterEach` |
| Parallel execution | `@TestSuite(parallel = true)` |
| Deterministic ordering | `order = ...` on `@Test`, `@BeforeAll`, `@BeforeEach`, `@AfterEach`, `@AfterAll` |
| Timeout per test | `@Test(timeoutMs = 500)` or `@Test(timeout = "PT30S")` |
| Delay before test | `@Test(delayMs = 300)` |
| Retry on failure | `@Test(retries = 2)` |
| Spring DI in suites | Annotate suite with `@Component` |
| Auto-discovery | Package-scoped scan via `testframework.scan-packages` |
| HTTP API | Built-in REST controller via Spring auto-configuration |
| Run cancellation | `POST /api/test-framework/runs/{runId}/cancel` |
| Structured reports | `ReportingPlugin` — JUnit XML, OTR XML, JSON |
| Incremental plugin hook | `SuiteRunListener#onTestCompleted(...)` |

---

## Spring DI in test suites

Annotate your suite with `@Component` and declare dependencies as constructor parameters — the framework injects them automatically:

```java
@Component
@TestSuite(name = "PaymentTestSuite")
public class PaymentTestSuite {

    private final PaymentService paymentService;

    public PaymentTestSuite(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Test(name = "chargeSucceeds")
    public void chargeSucceeds() {
        assertThat(paymentService.charge(100)).isTrue();
    }
}
```

---

## Reporting plugin

```kotlin
dependencies {
    implementation("no.kompilator:plugins:2.0.0")
}
```

Reports are written automatically after every suite run. Opt in to specific formats via `application.properties`:

```properties
testframework.reporting.enabled=true
testframework.reporting.output-dir=build/test-reports
testframework.reporting.formats=JUNIT_XML,OPEN_TEST_REPORTING_XML,JSON
```

Or build the plugin manually and register it as a listener:

```java
ReportingPlugin reporter = ReportingPlugin.builder()
        .outputDir(Path.of("build/test-reports"))
        .format(ReportFormat.JUNIT_XML)
        .format(ReportFormat.JSON)
        .build();

testFrameworkService.addListener(reporter);
```

Plugins can also observe per-test progress through `SuiteRunListener#onTestCompleted(...)`.

Async status polling exposes explicit progress and timing fields:

- `completedCount` / `totalCount` for cheap progress tracking
- `runStartedAtEpochMs` / `lastUpdatedAtEpochMs` on `SuiteRunStatus`
- `startedAtEpochMs` / `completedAtEpochMs` on each `TestCaseResult`
- `status = CANCELLED` when a caller stops a run explicitly

---

## Kotlin support

The framework works with Kotlin out of the box. Use the `kotlin("plugin.spring")` Gradle plugin to make `@Component`-annotated classes `open` for Spring proxying:

```kotlin
@Component
@TestSuite(name = "CalculatorTestSuite", description = "Tests for Calculator")
class CalculatorTestSuite(private val calculator: Calculator) {

    @Test(name = "addition")
    fun testAddition() {
        assertThat(calculator.add(2, 3)).isEqualTo(5)
    }

    @ParameterizedTest(name = "addition[{index}] {0}+{1}={2}")
    @CsvSource("1,2,3", "2,3,5")
    fun additionCases(left: Int, right: Int, expected: Int) {
        assertThat(calculator.add(left, right)).isEqualTo(expected)
    }
}
```

---

## Parameterized tests

Supported sources:

- `@ValueSource` for single-argument literals
- `@CsvSource` for multi-argument rows
- `@MethodSource` for provider methods returning `Stream`, `Iterable`, `Iterator`, or arrays
- `@EnumSource` for enum constants
- `@NullSource`, `@EmptySource`, and `@NullAndEmptySource` for single-argument nullable/empty cases

Example:

```java
@ParameterizedTest(name = "blank[{index}]={0}")
@NullAndEmptySource
@ValueSource(strings = {" ", "\t"})
public void rejectsBlankNames(String value) {
    assertThat(value == null || value.isBlank()).isTrue();
}
```

`@MethodSource` can emit either raw values for single-parameter tests or `Arguments.of(...)`
for multi-parameter invocations.

---

## Validation rules

The framework now fails fast during startup/registration when:

- suite names are duplicated
- test names are duplicated within a suite
- `timeoutMs < -1`
- `timeout` is not a valid ISO-8601 duration
- both `timeoutMs` and `timeout` are configured on the same test
- `delayMs < 0`
- `retries < 0`
- a `@Test` or lifecycle method is not `public`
- a `@Test` or lifecycle method is `static`
- a `@Test` or lifecycle method declares parameters
- an `@ParameterizedTest` declares zero parameters
- an `@ParameterizedTest` has no argument source
- `@ValueSource` configures more than one non-empty literal array
- `@NullSource` is used with primitive parameters

Ordering is deterministic:

- lower `order` values run first
- ties are resolved by method name

---

## Release

Local publish:

```bash
./situs/gradlew publishAllToMavenLocal
```

Parallel multi-module verification:

```bash
./situs/gradlew testAll
./situs/gradlew buildAll
```

Project-level parallel execution is enabled in `gradle.properties`, so independent
subprojects run concurrently where Gradle can schedule them safely.

Remote publish requires credentials and signing material in `~/.gradle/gradle.properties` or env vars:

```properties
centralUsername=...
centralPassword=...
signingKey=...
signingPassword=...
```

Equivalent environment variables:

```bash
export CENTRAL_USERNAME=...
export CENTRAL_PASSWORD=...
export SIGNING_KEY=...
export SIGNING_PASSWORD=...
```

Then publish:

```bash
./situs/gradlew publishRelease
```

The root release tasks are:

- `releaseCheck` — runs tests and Javadocs for the releasable modules and sample apps
- `publishAllToMavenLocal` — publishes `situs` and `plugins` to `mavenLocal`
- `publishRelease` — runs `releaseCheck` and then publishes `situs` and `plugins`

GitHub Actions release flow:

- push a tag like `v2.0.0`
- or trigger the `release` workflow manually with `version`
- the workflow runs `publishRelease` with `-Pversion=<tag>`
- required repository secrets:
  - `CENTRAL_USERNAME`
  - `CENTRAL_PASSWORD`
  - `SIGNING_KEY`
  - `SIGNING_PASSWORD`

See [`kotlin-spring-boot-sample-app`](kotlin-spring-boot-sample-app/README.md) for a full example.

---

## Build

```bash
# Build everything from the repo root
./situs/gradlew --project-dir . build

# Build a specific module
./situs/gradlew :situs:build
./situs/gradlew :plugins:build
```

## Run tests

```bash
./situs/gradlew :situs:test
./situs/gradlew :plugins:test
```

---

## Modules — further reading

- [`situs/README.md`](situs/README.md) — annotations, runtime engine, Spring integration, full API reference
- [`plugins/README.md`](plugins/README.md) — reporting plugin, report formats, configuration
- [`java-spring-boot-sample-app/README.md`](java-spring-boot-sample-app/README.md) — Java Spring Boot sample
- [`kotlin-spring-boot-sample-app/README.md`](kotlin-spring-boot-sample-app/README.md) — Kotlin Spring Boot sample

---

## Requirements

| | |
|---|---|
| Java | 21 |
| Gradle | Wrapper included — no local install needed |
| Spring Boot | 4.0.x (optional — core engine has no Spring dependency) |
