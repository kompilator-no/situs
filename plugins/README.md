# plugins

[![Maven Central](https://img.shields.io/maven-central/v/no.kompilator/plugins?label=Maven%20Central)](https://central.sonatype.com/artifact/no.kompilator/plugins)

Ready-made, drop-in runtime test suite plugins for the test framework.

Plugins react to events emitted by the main library and add secondary behaviour
such as report writing. They do not control suite discovery or execution.

Available listener hooks:

- `onTestCompleted(TestCompletedEvent)` for incremental per-test progress
- `onSuiteCompleted(SuiteCompletedEvent)` for final suite results

`TestCompletedEvent.testResult()` includes `startedAtEpochMs` and `completedAtEpochMs`,
so plugins can build live dashboards with real timing data instead of only durations.

---

## Package structure

```
no.kompilator.plugins
├── TestFrameworkPlugin.java        Marker interface implemented by all plugins
└── reporting/
    ├── ReportFormat.java           Enum: JUNIT_XML, OPEN_TEST_REPORTING_XML, JSON
    ├── SuiteReportWriter.java      Writes a TestSuiteResult to disk
    └── ReportingPlugin.java        Listens for completed suite events and writes reports
```

---

## Plugins

### `ReportingPlugin`

Writes a structured report file whenever the main library finishes running a suite.

Inspired by the [JUnit Platform Open Test Reporting](https://docs.junit.org/current/advanced-topics/junit-platform-reporting.html#open-test-reporting)
format — supports three output formats:

| Format | File name | Use case |
|---|---|---|
| `JUNIT_XML` | `TEST-{suiteName}.xml` | Jenkins, GitHub Actions, GitLab CI, IntelliJ, all CI tools |
| `OPEN_TEST_REPORTING_XML` | `{suiteName}-open-test-report.xml` | JUnit Platform OTR-compatible tooling |
| `JSON` | `{suiteName}-report.json` | Custom dashboards, archiving, post-processing |

#### Spring bean

```java
@Bean
public ReportingPlugin reportingPlugin() {
    return ReportingPlugin.builder()
            .outputDir(Path.of("build/test-reports"))
            .format(ReportFormat.JUNIT_XML)
            .format(ReportFormat.OPEN_TEST_REPORTING_XML)
            .build();
}
```

#### Use `SuiteReportWriter` standalone

If you already have a `TestSuiteResult`
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
    implementation("no.kompilator:plugins:0.1.0")
}
```

> `plugins` depends on `java-library` — you do **not** need to add `java-library`
> separately; it is pulled in transitively.

---

## Build

```bash
./java-library/gradlew :plugins:build
```

## Run tests

```bash
./java-library/gradlew :plugins:test
```
