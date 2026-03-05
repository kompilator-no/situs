# plugins

Ready-made, drop-in runtime test suite plugins for the test framework.

Plugins are pre-built `@RuntimeTestSuite` classes that you can use directly or
extend with minimal configuration — no boilerplate needed.

---

## Package structure

```
no.testframework.plugins
├── TestFrameworkPlugin.java        Marker interface implemented by all plugins
├── http/
│   ├── HttpHealthCheckPlugin.java          Builder-based — check many URLs at once
│   └── AnnotatedHttpHealthCheckPlugin.java  Extend to check one URL via the scanner
└── reporting/
    ├── ReportFormat.java           Enum: JUNIT_XML, OPEN_TEST_REPORTING_XML, JSON
    ├── SuiteReportWriter.java      Writes a TestSuiteExecutionResult to disk
    └── ReportingPlugin.java        Runs suites and writes reports automatically
```

---

## Plugins

### `HttpHealthCheckPlugin`

Sends a GET request to one or more URLs and asserts each returns a **2xx** status code.

#### Builder / programmatic usage

```java
HttpHealthCheckPlugin plugin = HttpHealthCheckPlugin.builder()
        .suiteName("Production Health Checks")
        .timeoutMs(5_000)
        .url("https://api.example.com/health")
        .url("https://auth.example.com/health")
        .build();

RuntimeTestSuiteRunner runner = new RuntimeTestSuiteRunner();
runner.runSuite(plugin);
```

#### One-liner factory

```java
HttpHealthCheckPlugin plugin = HttpHealthCheckPlugin.forUrls(
        "https://api.example.com/health",
        "https://auth.example.com/health"
);
```

#### Spring bean

```java
@Bean
public HttpHealthCheckPlugin httpHealthChecks() {
    return HttpHealthCheckPlugin.builder()
            .suiteName("Production Health Checks")
            .url("https://api.example.com/health")
            .url("https://auth.example.com/health")
            .build();
}
```

---

### `AnnotatedHttpHealthCheckPlugin`

Extend this class to create a single-URL health-check suite that is discovered
automatically by the classpath scanner and listed in the HTTP API.

```java
@Component   // optional — add when you want Spring DI
public class ApiHealthCheck extends AnnotatedHttpHealthCheckPlugin {
    public ApiHealthCheck() {
        super("https://api.example.com/health", 5_000);
    }
}
```

---

### `ReportingPlugin`

Runs one or more test suite classes and writes a structured report file for each one.

Inspired by the [JUnit Platform Open Test Reporting](https://docs.junit.org/current/advanced-topics/junit-platform-reporting.html#open-test-reporting)
format — supports three output formats:

| Format | File name | Use case |
|---|---|---|
| `JUNIT_XML` | `TEST-{suiteName}.xml` | Jenkins, GitHub Actions, GitLab CI, IntelliJ, all CI tools |
| `OPEN_TEST_REPORTING_XML` | `{suiteName}-open-test-report.xml` | JUnit Platform OTR-compatible tooling |
| `JSON` | `{suiteName}-report.json` | Custom dashboards, archiving, post-processing |

#### Quick start

```java
ReportingPlugin reporter = ReportingPlugin.builder()
        .suite(CalculatorTestSuite.class)
        .suite(PaymentTestSuite.class)
        .outputDir(Path.of("build/test-reports"))
        .format(ReportFormat.JUNIT_XML)
        .format(ReportFormat.OPEN_TEST_REPORTING_XML)
        .format(ReportFormat.JSON)
        .build();

List<TestSuiteExecutionResult> results = reporter.runAndReport();
```

#### Spring bean

```java
@Bean
public ReportingPlugin reportingPlugin() {
    return ReportingPlugin.builder()
            .suite(CalculatorTestSuite.class)
            .outputDir(Path.of("build/test-reports"))
            .format(ReportFormat.JUNIT_XML)
            .format(ReportFormat.OPEN_TEST_REPORTING_XML)
            .build();
}
```

#### Use `SuiteReportWriter` standalone

If you already have a `TestSuiteExecutionResult` (e.g. from `RuntimeTestSuiteRunner`)
you can write reports directly without the plugin wrapper:

```java
SuiteReportWriter writer = SuiteReportWriter.builder()
        .outputDir(Path.of("build/test-reports"))
        .format(ReportFormat.JUNIT_XML)
        .format(ReportFormat.OPEN_TEST_REPORTING_XML)
        .build();

writer.write(result);   // writes TEST-{name}.xml and {name}-open-test-report.xml
```

#### Open Test Reporting XML structure

```xml
<?xml version="1.0" encoding="UTF-8"?>
<e:events xmlns:e="https://schemas.opentest4j.org/reporting/events/0.1.0"
          xmlns:r="https://schemas.opentest4j.org/reporting/core/0.1.0">

  <e:started id="suite-1" name="My Suite" time="2026-03-05T10:00:00">
    <r:metadata><r:description>Integration checks</r:description></r:metadata>
  </e:started>

  <e:started id="test-1" name="addition" parentId="suite-1" time="2026-03-05T10:00:00"/>
  <e:finished id="test-1" time="2026-03-05T10:00:00" durationMs="12">
    <r:result status="SUCCESSFUL"/>
  </e:finished>

  <e:started id="test-2" name="flaky check" parentId="suite-1" time="2026-03-05T10:00:00"/>
  <e:finished id="test-2" time="2026-03-05T10:00:00" durationMs="340">
    <r:metadata><r:entry key="attempts" value="3"/></r:metadata>
    <r:result status="SUCCESSFUL"/>
  </e:finished>

  <e:started id="test-3" name="division" parentId="suite-1" time="2026-03-05T10:00:00"/>
  <e:finished id="test-3" time="2026-03-05T10:00:00" durationMs="5">
    <r:result status="FAILED">
      <r:failure type="org.opentest4j.AssertionFailedError" message="expected &lt;5&gt; but was &lt;0&gt;"/>
    </r:result>
  </e:finished>

  <e:finished id="suite-1" time="2026-03-05T10:00:00">
    <r:result status="FAILED"/>
  </e:finished>
</e:events>
```

The `attempts` metadata entry is written whenever a test was retried — making retry
information visible to any OTR-compatible tool.

---

## Adding to your project

### Gradle

```kotlin
dependencies {
    implementation("no.testframework:plugins:0.1.0")
}
```

> `plugins` depends on `java-library` — you do **not** need to add `java-library`
> separately; it is pulled in transitively.

---

## Build

```bash
./gradlew :plugins:build
```

## Run tests

```bash
./gradlew :plugins:test
```


Plugins are pre-built `@RuntimeTestSuite` classes that you can use directly or
extend with minimal configuration — no boilerplate needed.

---

## Package structure

```
no.testframework.plugins
├── TestFrameworkPlugin.java   Marker interface implemented by all plugins
└── http/
    ├── HttpHealthCheckPlugin.java          Builder-based — check many URLs at once
    └── AnnotatedHttpHealthCheckPlugin.java  Extend to check one URL via the scanner
```

---

## Plugins

### `HttpHealthCheckPlugin`

Sends a GET request to one or more URLs and asserts each returns a **2xx** status code.

#### Builder / programmatic usage

```java
// Check multiple URLs in one suite run
HttpHealthCheckPlugin plugin = HttpHealthCheckPlugin.builder()
        .suiteName("Production Health Checks")
        .timeoutMs(5_000)
        .url("https://api.example.com/health")
        .url("https://auth.example.com/health")
        .url("https://payments.example.com/health")
        .build();

RuntimeTestSuiteRunner runner = new RuntimeTestSuiteRunner();
runner.runSuite(plugin);
```

#### One-liner factory

```java
HttpHealthCheckPlugin plugin = HttpHealthCheckPlugin.forUrls(
        "https://api.example.com/health",
        "https://auth.example.com/health"
);
```

#### Spring bean registration

```java
@Bean
public HttpHealthCheckPlugin httpHealthChecks() {
    return HttpHealthCheckPlugin.builder()
            .suiteName("Production Health Checks")
            .url("https://api.example.com/health")
            .url("https://auth.example.com/health")
            .build();
}
```

---

### `AnnotatedHttpHealthCheckPlugin`

Extend this class to create a single-URL health-check suite that is discovered
automatically by the classpath scanner and listed in the HTTP API.

```java
// One subclass per endpoint — each appears as its own suite in the API
@Component   // optional — add when you want Spring DI
public class ApiHealthCheck extends AnnotatedHttpHealthCheckPlugin {

    public ApiHealthCheck() {
        super("https://api.example.com/health", 5_000);
    }
}
```

The suite name defaults to `"HTTP Health Check"`. Override via the constructor
if you want a custom name:

```java
public class ApiHealthCheck extends AnnotatedHttpHealthCheckPlugin {
    public ApiHealthCheck() {
        super("API Health Check", "https://api.example.com/health", 5_000);
    }
}
```

#### Running via HTTP API

```bash
# Start the check
curl -X POST http://localhost:8080/api/test-framework/suites/ApiHealthCheck/run

# Poll until COMPLETED
curl http://localhost:8080/api/test-framework/runs/{runId}/status
```

---

## Adding to your project

### Gradle

```kotlin
dependencies {
    implementation("no.testframework:plugins:0.1.0")
}
```

> `plugins` depends on `java-library` — you do **not** need to add `java-library`
> separately; it is pulled in transitively.

---

## Build

```bash
./gradlew :plugins:build
```

## Run tests

```bash
./gradlew :plugins:test
```
