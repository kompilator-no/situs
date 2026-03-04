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

## Annotations

| Annotation | Target | Description |
|---|---|---|
| `@RuntimeTestSuite` | Class | Marks a class as a runtime test suite. Accepts `name` and `description`. |
| `@RunTimeTest` | Method | Marks a method as a test case. Accepts `name`, `description`, and `timeoutMs` (0 = no timeout). |
| `@BeforeAll` | Method | Runs **once** before all tests in the suite. |
| `@BeforeEach` | Method | Runs before **each** individual test. |
| `@AfterEach` | Method | Runs after **each** individual test (always runs, even on failure or timeout). |
| `@AfterAll` | Method | Runs **once** after all tests in the suite. |

---

## Domain model

| Class | Description |
|---|---|
| `TestSuiteDefinition` | Metadata for a discovered suite (name, description, class, test cases). |
| `TestCaseDefinition` | Metadata for a discovered test (name, description, method, timeoutMs). |
| `TestSuiteExecutionResult` | Result of running a suite (suite name, per-case results, pass/fail counts). |
| `TestCaseExecutionResult` | Result of running a single test (name, passed, error message, duration ms). |

---

## Runtime engine

| Class | Description |
|---|---|
| `TestSuiteRegistry` | Scans a set of candidate classes and returns all `TestSuiteDefinition`s. |
| `TestRunner` | Runs all `@RunTimeTest` methods on a class, enforcing per-test timeouts. |
| `RuntimeTestSuiteRunner` | Orchestrates `TestRunner` for a `@RuntimeTestSuite` class and prints the report. |
| `SuiteReporter` | Formats a structured, box-drawing suite report into the SLF4J logger. |

---

## Usage

### 1. Define a test suite

```java
@RuntimeTestSuite(name = "My Suite", description = "Integration checks")
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
        assert 2 + 2 == 4;
    }

    @RunTimeTest(name = "check flag")
    public void checkFlag() {
        assert Boolean.TRUE;
    }

    @RunTimeTest(name = "check remote call", timeoutMs = 3000)
    public void checkRemoteCall() {
        // fails (and is cancelled) if it takes longer than 3 seconds
    }
}
```

### 2. Run a suite directly

```java
RuntimeTestSuiteRunner runner = new RuntimeTestSuiteRunner();
TestSuiteExecutionResult result = runner.runSuite(MyTestSuite.class);
// SuiteReporter automatically prints a formatted report to the log
```

### 3. Discover suites from a set of classes

```java
Set<Class<?>> candidates = Set.of(MyTestSuite.class, AnotherSuite.class);
List<TestSuiteDefinition> suites = new TestSuiteRegistry().getAllSuites(candidates);
```

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

Spring Web and Spring Context are **compile-only** dependencies.  
Add them to your application's classpath to enable the HTTP layer.

### Endpoints

All run endpoints are **async by default** — they return a `runId` immediately and you poll for results.

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/test-framework/status` | Health check — returns `"OK"`. |
| `GET` | `/api/test-framework/suites` | Lists all registered suites and their test cases. |
| `POST` | `/api/test-framework/suites/run` | Start a suite run (suite name in request body). Returns `{"runId":"..."}`. |
| `POST` | `/api/test-framework/suites/{suiteName}/run` | Start a suite run by path. Returns `{"runId":"..."}`. |
| `POST` | `/api/test-framework/suites/{suiteName}/tests/{testName}/run` | Start a single-test run. Returns `{"runId":"..."}`. |
| `GET` | `/api/test-framework/runs/{runId}/status` | Poll live status (`PENDING` → `RUNNING` → `COMPLETED`) and results. |

### Status response

```json
{
  "runId": "550e8400-...",
  "suiteName": "My Suite",
  "status": "RUNNING",
  "completedResults": [
    { "name": "check addition", "passed": true,  "errorMessage": null, "durationMs": 12 }
  ],
  "passedCount": 1,
  "failedCount": 0
}
```

`status` progresses through: `PENDING` → `RUNNING` → `COMPLETED`

### Spring wiring

```java
@Configuration
public class TestFrameworkConfig {

    @Bean
    public TestFrameworkService testFrameworkService() {
        return new TestFrameworkService(Set.of(MyTestSuite.class, AnotherSuite.class));
    }

    @Bean
    public TestFrameworkController testFrameworkController(TestFrameworkService service) {
        return new TestFrameworkController(service);
    }
}
```
