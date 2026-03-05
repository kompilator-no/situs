# Java Library

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

## Run the sample suite manually

```bash
.\gradlew.bat runSuite      # Windows
./gradlew runSuite          # WSL / Linux / macOS
```

This executes `RuntimeTestSuiteRunnerMain` which runs the `SampleTestSuite` and prints the results to the console.

## Publish to local Maven repository

```bash
./gradlew publishToMavenLocal
```

---

## Package structure

```
no.testframework.javalibrary
├── annotations/     Pure-Java annotations — no Spring dependency
├── domain/          Internal domain objects (not part of the public API)
├── model/           Shared public models — no Spring dependency (TestSuite, TestCase, …)
├── runtime/         Pure-Java execution engine (TestRunner, ClasspathScanner, …)
└── spring/          Spring integration layer (optional)
    └── model/       Spring/Jackson-specific models (SuiteRunStatus)
```

> **No Spring required for core usage.**  
> `annotations/`, `domain/`, `model/`, and `runtime/` have no Spring dependency.  
> Add `spring-context` and `spring-web` to your classpath only when you need the HTTP API.

---

## Annotations

| Annotation | Target | Description |
|---|---|---|
| `@RuntimeTestSuite` | Class | Marks a class as a runtime test suite. Accepts `name`, `description`, and `parallel`. |
| `@RunTimeTest` | Method | Marks a method as a test case. Accepts `name`, `description`, `timeoutMs` (0 = framework default 10 s, -1 = no timeout), and `delayMs`. |
| `@BeforeAll` | Method | Runs **once** before all tests in the suite. |
| `@BeforeEach` | Method | Runs before **each** individual test. |
| `@AfterEach` | Method | Runs after **each** individual test (always runs, even on failure or timeout). |
| `@AfterAll` | Method | Runs **once** after all tests in the suite. |

---

## Domain model (internal)

These classes are used internally by the runtime engine and are not part of the public API.

| Class | Description |
|---|---|
| `TestSuiteDefinition` | Metadata for a discovered suite (name, description, class, test cases, parallel flag). |
| `TestCaseDefinition` | Metadata for a discovered test (name, description, method, timeoutMs, delayMs). |
| `TestSuiteExecutionResult` | Result of running a suite (suite name, per-case results, pass/fail counts). |
| `TestCaseExecutionResult` | Result of running a single test (name, passed, error message, exception type, stack trace, duration ms). |

## Shared model (public API)

These classes live in `no.testframework.javalibrary.model` and have no Spring dependency.

| Class | Description |
|---|---|
| `TestSuite` | Suite descriptor returned by the service and accepted as a JSON request body. |
| `TestCase` | Test case descriptor nested inside `TestSuite`. |
| `TestCaseResult` | Result of a single test — name, passed, error message, exception type, stack trace, duration ms. |
| `TestSuiteResult` | Aggregate suite result — individual `TestCaseResult`s plus pass/fail counts. |

---

## Runtime engine

| Class | Description |
|---|---|
| `ClasspathScanner` | Scans the classpath (or a specific package) for classes annotated with `@RuntimeTestSuite`. |
| `TestSuiteRegistry` | Inspects a set of candidate classes and builds `TestSuiteDefinition`s. |
| `TestRunner` | Runs all `@RunTimeTest` methods on a class, enforcing per-test timeouts (sequential or parallel). |
| `RuntimeTestSuiteRunner` | Convenience facade — runs a `@RuntimeTestSuite` class end-to-end and prints the report. |
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
@RuntimeTestSuite(name = "My Suite", description = "Integration checks", parallel = false)
public class MyTestSuite {

    @BeforeAll
    public void setupSuite() { /* runs once before all tests */ }

    @BeforeEach
    public void setupTest() { /* runs before each test */ }

    @AfterEach
    public void tearDownTest() { /* runs after each test, even on failure */ }

    @AfterAll
    public void tearDownSuite() { /* runs once after all tests */ }

    @RunTimeTest(name = "check addition")
    public void checkAddition() {
        assertThat(2 + 2).isEqualTo(4);
    }

    @RunTimeTest(name = "check remote call", timeoutMs = 3000)
    public void checkRemoteCall() {
        // cancelled and recorded as failed if it takes longer than 3 seconds
    }
}
```

### 2. Run a suite directly (no Spring needed)

```java
RuntimeTestSuiteRunner runner = new RuntimeTestSuiteRunner();
TestSuiteExecutionResult result = runner.runSuite(MyTestSuite.class);
// SuiteReporter automatically prints a formatted report to the log
```

### 3. Scan and run from a set of classes

```java
// Explicit set
Set<Class<?>> candidates = Set.of(MyTestSuite.class, AnotherSuite.class);
List<TestSuiteDefinition> suites = new TestSuiteRegistry().getAllSuites(candidates);

// Full classpath scan (finds all @RuntimeTestSuite classes automatically)
Set<Class<?>> all = ClasspathScanner.findAllRuntimeTestSuites();

// Package-scoped scan
Set<Class<?>> scoped = ClasspathScanner.findRuntimeTestSuites("com.example.tests");
```

### 4. Parallel execution

Set `parallel = true` on `@RuntimeTestSuite` to run all tests concurrently:

```java
@RuntimeTestSuite(name = "Parallel Suite", parallel = true)
public class MyParallelSuite {
    // each test gets its own instance — must not share mutable state
}
```

### 5. Dependency injection in test suite classes

When running inside Spring, annotate your suite class with `@Component` (or any Spring stereotype). The framework will resolve it from the `ApplicationContext` instead of calling `new`, so all constructor and field dependencies are injected automatically.

```java
@Component
@RuntimeTestSuite(name = "Payment Suite", description = "Live payment checks")
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

    @RunTimeTest(name = "send notification")
    public void sendNotification() {
        assertThat(notificationService.send("test")).isNotNull();
    }
}
```

> **Non-bean suites continue to work unchanged.** If the suite class is not a Spring bean the framework falls back to plain reflection instantiation (`new SuiteClass()`), so you do not have to annotate every suite.

This is handled transparently by `SpringInstanceFactory` — no extra configuration is needed. It is wired automatically by `RuntimeTestAutoConfiguration` and `@EnableRuntimeTests`.

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
╠══════════════════════════════════════════════════════════════╣
║  PASSED: 1   FAILED: 2   TOTAL: 3   TIME: 3017 ms           ║
╚══════════════════════════════════════════════════════════════╝
```

Icons: `✔` passed · `✘` failed · `⏱` timed out

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
      "durationMs": 12
    }
  ],
  "passedCount": 1,
  "failedCount": 0
}
```

`status` progresses: `PENDING` → `RUNNING` → `COMPLETED`

On failure, `errorMessage`, `exceptionType`, and `stackTrace` are populated so you can diagnose the problem without needing server log access.
