# test-framework

An annotation-driven **runtime test framework** for Java 21. Define test suites as plain Java classes, run them on demand via a REST API or programmatically, and get structured reports in JUnit XML, Open Test Reporting XML, or JSON.

Unlike JUnit, tests run **at runtime in production-like environments** instead of only at build time. Suites support Spring dependency injection, parallel execution, timeouts, delays, retries, and deterministic ordering.

---

## Repository structure

```
test-framework/
├── java-library/                   Core library — annotations, engine, Spring integration
├── plugins/                        Ready-made plugins (reporting: JUnit XML, OTR XML, JSON)
├── java-spring-boot-sample-app/    Java Spring Boot example using the library
└── kotlin-spring-boot-sample-app/  Kotlin Spring Boot example using the library
```

| Module | Artifact | Description |
|---|---|---|
| `java-library` | `no.certusdev.testframework:java-library` | Annotations, runtime engine, HTTP API |
| `plugins` | `no.certusdev.testframework:plugins` | Reporting plugin — writes structured test reports |
| `java-spring-boot-sample-app` | — | Java sample app (not published) |
| `kotlin-spring-boot-sample-app` | — | Kotlin sample app (not published) |

---

## Supported API Surface

Supported packages:

- `no.certusdev.testframework.javalibrary.annotations`
- `no.certusdev.testframework.javalibrary.model`
- `no.certusdev.testframework.javalibrary.plugin`
- `no.certusdev.testframework.javalibrary.service`
- `no.certusdev.testframework.javalibrary.spring`
- `no.certusdev.testframework.javalibrary.spring.model`

Internal packages that may change without notice:

- `no.certusdev.testframework.javalibrary.domain`
- `no.certusdev.testframework.javalibrary.runtime`

Build against the supported packages only.

---

## Quick start

### 1. Add the dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("no.certusdev.testframework:java-library:0.1.0")

    // Optional — adds structured report writing (pulls in java-library transitively)
    implementation("no.certusdev.testframework:plugins:0.1.0")
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

    @Test(name = "divisionByZero", timeoutMs = 500, order = 2)
    public void testDivisionByZero() {
        assertThatThrownBy(() -> 1 / 0)
                .isInstanceOf(ArithmeticException.class);
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
```

---

## Key features

| Feature | Annotation / API |
|---|---|
| Define a test suite | `@TestSuite` |
| Define a test method | `@Test` |
| Setup / teardown | `@BeforeAll`, `@AfterAll`, `@BeforeEach`, `@AfterEach` |
| Parallel execution | `@TestSuite(parallel = true)` |
| Deterministic ordering | `order = ...` on `@Test`, `@BeforeAll`, `@BeforeEach`, `@AfterEach`, `@AfterAll` |
| Timeout per test | `@Test(timeoutMs = 500)` |
| Delay before test | `@Test(delayMs = 300)` |
| Retry on failure | `@Test(retries = 2)` |
| Spring DI in suites | Annotate suite with `@Component` |
| Auto-discovery | Package-scoped scan via `testframework.scan-packages` |
| HTTP API | Built-in REST controller via Spring auto-configuration |
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
    implementation("no.certusdev.testframework:plugins:0.1.0")
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
}
```

---

## Validation rules

The framework now fails fast during startup/registration when:

- suite names are duplicated
- test names are duplicated within a suite
- `timeoutMs < -1`
- `delayMs < 0`
- `retries < 0`
- a `@Test` or lifecycle method is not `public`
- a `@Test` or lifecycle method is `static`
- a `@Test` or lifecycle method declares parameters

Ordering is deterministic:

- lower `order` values run first
- ties are resolved by method name

---

## Release

Local publish:

```bash
./java-library/gradlew publishAllToMavenLocal
```

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
./java-library/gradlew publishRelease
```

The root release tasks are:

- `releaseCheck` — runs tests and Javadocs for the releasable modules and sample apps
- `publishAllToMavenLocal` — publishes `java-library` and `plugins` to `mavenLocal`
- `publishRelease` — runs `releaseCheck` and then publishes `java-library` and `plugins`

GitHub Actions release flow:

- push a tag like `v0.1.0`
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
./java-library/gradlew --project-dir . build

# Build a specific module
./java-library/gradlew :java-library:build
./java-library/gradlew :plugins:build
```

## Run tests

```bash
./java-library/gradlew :java-library:test
./java-library/gradlew :plugins:test
```

---

## Modules — further reading

- [`java-library/README.md`](java-library/README.md) — annotations, runtime engine, Spring integration, full API reference
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
