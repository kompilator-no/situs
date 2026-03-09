# Java Library

[![Maven Central](https://img.shields.io/maven-central/v/no.kompilator/java-library?label=Maven%20Central)](https://central.sonatype.com/artifact/no.kompilator/java-library)

Annotation-driven runtime test framework for Java 21.  
Define test suites as plain Java classes, run them via the engine directly or over HTTP, and poll for live status while long-running tests execute.

---

## Requirements

| | |
|---|---|
| Java | 21 |
| Build tool | Gradle (wrapper included — no local install needed) |

---

## Build

```bash
# Windows CMD / PowerShell
.\gradlew.bat build

# WSL / macOS / Linux
./gradlew build
```

The `build` task compiles sources, runs all tests, and produces the JAR under `build/libs/`.

## Run tests only

```bash
.\gradlew.bat test          # Windows
./gradlew test              # WSL / Linux / macOS
```

Test reports are written to `build/reports/tests/test/index.html`.

## Publish to local Maven repository

```bash
./gradlew -p .. publishAllToMavenLocal
```

## Release publish

From the repo root:

```bash
./java-library/gradlew publishRelease
```

This runs the release verification tasks and then publishes the releasable modules.

---

## Package structure

```
no.kompilator.javalibrary
├── annotations/     Public annotations (`@TestSuite`, `@Test`, lifecycle annotations)
├── domain/          Internal domain objects (not part of the public API)
├── model/           Shared public models — no Spring dependency
├── plugin/          Public plugin SPI (`SuiteRunListener`, `TestCompletedEvent`, `SuiteCompletedEvent`)
├── runtime/         Internal execution engine (TestRunner, ClasspathScanner, …)
├── service/         Public orchestration API (`TestFrameworkService`)
└── spring/          Spring integration layer (optional)
```

> **No Spring required for core usage.**  
> `annotations/`, `domain/`, `model/`, `plugin/`, `runtime/`, and `service/` have no Spring dependency.  
> Add `spring-context` and `spring-web` to your classpath only when you need the HTTP API.

---

## Annotations

| Annotation | Target | Description |
|---|---|---|
| `@TestSuite` | Class | Marks a class as a runtime test suite. Accepts `name`, `description`, and `parallel`. |
| `@Test` | Method | Marks a method as a test case. Accepts `name`, `description`, `order`, `timeoutMs` (0 = framework default 10 s, -1 = no timeout), `delayMs`, and `retries`. |
| `@BeforeAll` | Method | Runs **once** before all tests in the suite. |
| `@BeforeEach` | Method | Runs before **each** individual test. |
| `@AfterEach` | Method | Runs after **each** individual test (always runs, even on failure or timeout). |
| `@AfterAll` | Method | Runs **once** after all tests in the suite. |

### `retries`

The `retries` attribute on `@Test` re-runs a failing test up to `retries` additional times before recording it as failed.

| Value | Behaviour |
|---|---|
| `0` (default) | No retries — a single failure is recorded immediately. |
| `n > 0` | Up to `n + 1` total attempts. Returns as soon as one attempt passes. If all fail, the last failure is recorded. |

Each retry runs the full per-test lifecycle (`@BeforeEach` → test body → `@AfterEach`).  
The `attempts` field in the result shows how many attempts were actually made.

```java
@Test(name = "flaky check", retries = 2)   // up to 3 attempts
public void checkExternalService() {
    assertThat(externalService.isReady()).isTrue();
}
```

---

## Domain model (internal)

These classes are used internally by the runtime engine and are not part of the public API.

| Class | Description |
|---|---|
| `TestSuiteDefinition` | Metadata for a discovered suite (name, description, class, test cases, parallel flag). |
| `TestCaseDefinition` | Metadata for a discovered test (name, description, method, timeoutMs, delayMs, retries). |
| `TestCaseExecutionResult` | Result of running a single test (name, passed, error message, exception type, stack trace, duration ms, attempts, start/completion timestamps). |

## Shared model (public API)

These classes live in `no.kompilator.javalibrary.model` and have no Spring dependency.

| Class | Description |
|---|---|
| `TestSuite` | Public suite descriptor returned by the service. |
| `TestCase` | Public test case descriptor nested inside `TestSuite`. |
| `TestCaseResult` | Result of a single test — name, passed, error message, exception type, stack trace, duration ms, **attempts**, `startedAtEpochMs`, `completedAtEpochMs`. |
| `TestSuiteResult` | Aggregate suite result — individual `TestCaseResult`s plus pass/fail counts. |
| `SuiteRunStatus` | Snapshot of an asynchronous run exposed by the service/HTTP API, including `completedCount`, `totalCount`, `runStartedAtEpochMs`, `lastUpdatedAtEpochMs`, and terminal states such as `CANCELLED`. |

---

## Runtime engine

| Class | Description |
|---|---|
| `ClasspathScanner` | Scans the classpath (or a specific package) for classes annotated with `@TestSuite`. |
| `TestSuiteRegistry` | Inspects a set of candidate classes and builds `TestSuiteDefinition`s. |
| `TestRunner` | Runs all `@Test` methods on a class, enforcing per-test ordering, timeouts, and retries (sequential or parallel). |
| `RuntimeTestSuiteRunner` | Convenience facade — runs a `@TestSuite` class end-to-end and prints the report. |
| `SuiteReporter` | Formats a structured, box-drawing suite report into the SLF4J logger. |
| `InstanceFactory` | Strategy interface for creating suite instances. Default: reflection. Swap for DI support. |

## Spring engine

| Class | Description |
|---|---|
| `SpringInstanceFactory` | Resolves suite instances from the Spring `ApplicationContext`; falls back to reflection for non-beans. |

---

## Usage

### 1. Define a test suite

```java
@TestSuite(name = "My Suite", description = "Integration checks", parallel = false)
public class MyTestSuite {

    @BeforeAll
    public void setupSuite() { /* runs once before all tests */ }

    @BeforeEach
    public void setupTest() { /* runs before each test */ }

    @AfterEach
    public void tearDownTest() { /* runs after each test, even on failure */ }

    @AfterAll
    public void tearDownSuite() { /* runs once after all tests */ }

    @Test(name = "check addition")
    public void checkAddition() {
        assertThat(2 + 2).isEqualTo(4);
    }

    @Test(name = "check remote call", timeoutMs = 3_000)
    public void checkRemoteCall() {
        // cancelled and recorded as failed if it takes longer than 3 seconds
    }

    @Test(name = "flaky check", retries = 2)
    public void checkFlakyService() {
        // retried up to 2 extra times on failure (3 total attempts)
        assertThat(externalService.isReady()).isTrue();
    }
}
```

### 2. Run a suite directly (no Spring needed)

```java
RuntimeTestSuiteRunner runner = new RuntimeTestSuiteRunner();
List<TestCaseExecutionResult> result = runner.runSuite(MyTestSuite.class);
// SuiteReporter automatically prints a formatted report to the log as part of the runner
```

### 3. Scan and run from a set of classes

```java
// Explicit set
Set<Class<?>> candidates = Set.of(MyTestSuite.class, AnotherSuite.class);
List<TestSuiteDefinition> suites = new TestSuiteRegistry().getAllSuites(candidates);

// Full classpath scan (finds all @TestSuite classes automatically)
Set<Class<?>> all = ClasspathScanner.findAllTestSuites();

// Package-scoped scan
Set<Class<?>> scoped = ClasspathScanner.findTestSuites("com.example.tests");
```

### 4. Parallel execution

Set `parallel = true` on `@TestSuite` to run all tests concurrently:

```java
@TestSuite(name = "Parallel Suite", parallel = true)
public class MyParallelSuite {
    // each test gets its own instance — must not share mutable state
}
```

### 5. Retries

```java
@TestSuite(name = "Resilience Suite")
public class ResilienceSuite {

    // passes as soon as one attempt succeeds — result shows attempts = 1
    @Test(name = "stable check", retries = 3)
    public void stableCheck() {
        assertThat(1 + 1).isEqualTo(2);
    }

    // fails twice, passes on attempt 3 — result shows attempts = 3
    @Test(name = "flaky check", retries = 2)
    public void flakyCheck() {
        assertThat(externalService.isReady()).isTrue();
    }

    // all 3 attempts fail — result shows passed = false, attempts = 3
    @Test(name = "always fails", retries = 2)
    public void alwaysFails() {
        assertThat(false).isTrue();
    }
}
```

### 6. Dependency injection in test suite classes

When running inside Spring, annotate your suite class with `@Component` (or any Spring stereotype). The framework will resolve it from the `ApplicationContext` instead of calling `new`, so all constructor dependencies are injected automatically.

```java
@Component
@TestSuite(name = "Payment Suite", description = "Live payment checks")
public class PaymentTestSuite {

    private final PaymentService paymentService;
    private final NotificationService notificationService;

    public PaymentTestSuite(PaymentService paymentService,
                            NotificationService notificationService) {
        this.paymentService = paymentService;
        this.notificationService = notificationService;
    }

    @RunTimeTest(name = "process payment")
    public void processPayment() {
        assertThat(paymentService.process(100)).isTrue();
    }

    @RunTimeTest(name = "send notification", retries = 1)
    public void sendNotification() {
        assertThat(notificationService.send("test")).isNotNull();
    }
}
```

### 7. Cancel an async run

Programmatic API:

```java
String runId = testFrameworkService.startSuiteAsync("My Suite");

SuiteRunStatus cancelled = testFrameworkService.cancelRun(runId);
assert cancelled.getStatus() == SuiteRunStatus.Status.CANCELLED;
```

Spring HTTP API:

```bash
curl -X POST http://localhost:8080/api/test-framework/runs/<runId>/cancel
```

Cancelled runs are terminal and report:

- `status = CANCELLED`
- partial `completedResults` collected before cancellation, if any
- `runErrorType = java.util.concurrent.CancellationException`

> **Non-bean suites continue to work unchanged.** If the suite class is not a Spring bean the framework falls back to plain reflection instantiation (`new SuiteClass()`), so you do not have to annotate every suite.

This is handled transparently by `SpringInstanceFactory` — wired automatically by `RuntimeTestAutoConfiguration` and `@EnableRuntimeTests`.

---

## Kotlin support

The library works with Kotlin without any changes. A Kotlin consumer needs two things in `build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"   // makes @Component classes open automatically
}

dependencies {
    implementation("no.kompilator:java-library:<version>")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")  // for Jackson
}
```

```kotlin
@Component
@RuntimeTestSuite(name = "My Suite")
class MyTestSuite(private val myService: MyService) {   // constructor injection works as-is

    @RunTimeTest(name = "check something", retries = 2)
    fun checkSomething() {
        assertThat(myService.doSomething()).isEqualTo("expected")
    }
}
```

See [`kotlin-spring-boot-sample-app`](../kotlin-spring-boot-sample-app) for a full working example.

---

## Log output

After each suite run `SuiteReporter` prints a structured banner:

```
╔══════════════════════════════════════════════════════════════╗
║  TEST SUITE: My Suite                                        ║
║  Description: Integration checks                            ║
╠══════════════════════════════════════════════════════════════╣
║  ✔  check addition                            (   12 ms)    ║
║  ✘  check flag                                (    4 ms)    ║
║     → expected <true> but was <false>                        ║
║  ⏱  check remote call                         ( 3001 ms)    ║
║     → Test timed out after 3000ms                            ║
║  ✔  flaky check                               (  340 ms)    ║
║     ↻ passed on attempt 3                                    ║
╠══════════════════════════════════════════════════════════════╣
║  PASSED: 2   FAILED: 2   TOTAL: 4   TIME: 3357 ms           ║
╚══════════════════════════════════════════════════════════════╝
```

Icons: `✔` passed · `✘` failed · `⏱` timed out · `↻` retried

---

## Spring HTTP API (optional)

Spring Web and Spring Context are **compile-only** dependencies in this library.  
Add them to your application's classpath to enable the HTTP layer.

### Activation

#### Spring Boot (zero configuration)
Just add the library JAR to your classpath. Spring Boot auto-configuration activates the framework automatically via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

#### Spring Boot (explicit opt-in)
```java
@SpringBootApplication
@EnableRuntimeTests
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

#### Plain Spring (non-Boot)
```java
@Configuration
@EnableRuntimeTests
public class AppConfig {
    // no extra beans needed — suites are discovered automatically
}
```

#### Manual wiring (custom suite set)
```java
@Configuration
public class TestFrameworkConfig {

    @Bean
    public TestFrameworkService testFrameworkService(ApplicationContext ctx) {
        // full classpath scan + Spring DI in suite classes (recommended)
        return new TestFrameworkService(ctx);

        // or package-scoped scan + Spring DI
        // return new TestFrameworkService(ctx, "com.example.tests");

        // or explicit set + Spring DI
        // return new TestFrameworkService(ctx, Set.of(MyTestSuite.class));

        // or without Spring DI (suite classes must have a no-arg constructor)
        // return new TestFrameworkService(Set.of(MyTestSuite.class));
    }

    @Bean
    public TestFrameworkController testFrameworkController(TestFrameworkService service) {
        return new TestFrameworkController(service);
    }
}
```

### Endpoints

All run endpoints are **asynchronous** — they return a `runId` immediately; poll `/runs/{runId}/status` until `status == "COMPLETED"`.

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/test-framework/status` | Health check — returns `"OK"`. |
| `GET` | `/api/test-framework/suites` | Lists all registered suites and their test cases. |
| `POST` | `/api/test-framework/suites/run` | Start a suite run (suite name in JSON body). Returns `{"runId":"…"}`. |
| `POST` | `/api/test-framework/suites/{suiteName}/run` | Start a suite run by path parameter. Returns `{"runId":"…"}`. |
| `POST` | `/api/test-framework/suites/{suiteName}/tests/{testName}/run` | Start a single-test run. Returns `{"runId":"…"}`. |
| `GET` | `/api/test-framework/runs/{runId}/status` | Poll live status and results. |

### Error responses

| Status | Condition |
|---|---|
| `404 Not Found` | Suite or test name not recognised. |
| `409 Conflict` | Suite or test is already `PENDING` or `RUNNING`. |

### Status response

```json
{
  "runId": "550e8400-e29b-41d4-a716-446655440000",
  "suiteName": "My Suite",
  "status": "RUNNING",
  "completedResults": [
    {
      "name": "check addition",
      "passed": true,
      "errorMessage": null,
      "exceptionType": null,
      "stackTrace": null,
      "durationMs": 12,
      "attempts": 1
    },
    {
      "name": "flaky check",
      "passed": true,
      "errorMessage": null,
      "exceptionType": null,
      "stackTrace": null,
      "durationMs": 340,
      "attempts": 3
    }
  ],
  "passedCount": 2,
  "failedCount": 0
}
```

`status` progresses: `PENDING` → `RUNNING` → `COMPLETED`

`attempts` is `1` when the test passed or failed on the first try with no retries used.  
On failure, `errorMessage`, `exceptionType`, and `stackTrace` are populated so you can diagnose the problem without needing server log access.

---

## Sample applications

| App | Language | Location |
|---|---|---|
| Java Spring Boot sample | Java 21 | [`java-spring-boot-sample-app`](../java-spring-boot-sample-app) |
| Kotlin Spring Boot sample | Kotlin 2.x | [`kotlin-spring-boot-sample-app`](../kotlin-spring-boot-sample-app) |

Both apps demonstrate auto-discovery, Spring DI injection, parallel execution, timeouts, delays, and retries.
