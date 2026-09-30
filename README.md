# Situs

[![Maven Central - situs](https://img.shields.io/maven-central/v/no.kompilator/situs?label=situs)](https://central.sonatype.com/artifact/no.kompilator/situs)
[![Maven Central - plugins](https://img.shields.io/maven-central/v/no.kompilator/plugins?label=plugins)](https://central.sonatype.com/artifact/no.kompilator/plugins)

**Runtime integration testing for Java microservices.**

Situs lets you define integration tests alongside your application and execute them **inside a running microservice environment**.

Instead of only testing integrations during CI, Situs lets you verify that your deployed service can actually communicate with the systems around it:

- other microservices
- REST APIs
- databases
- Kafka and message brokers
- authentication services
- external APIs
- infrastructure dependencies

Think of it as:

> **JUnit-style tests for your running microservice environment.**

---

## Why Situs?

Unit tests tell you that your code works.

Build-time integration tests tell you that your application works in a controlled test environment.

But many microservice failures only appear **after deployment**.

For example:

```text
Wrong service URL
        ↓
Broken DNS / service discovery
        ↓
Missing secret
        ↓
Invalid certificate
        ↓
Kafka ACL problem
        ↓
Network policy blocking traffic
        ↓
Different API version deployed
        ↓
Environment-specific configuration error
```

These problems are difficult to catch with tests that only run during the build.

Situs runs tests **inside the deployed application**, using the same configuration and dependencies as the service itself.

```text
                  Kubernetes / Docker / VM

┌─────────────────────────────────────────────────────┐
│                                                     │
│   ┌───────────────────────────────┐                 │
│   │         Order Service         │                 │
│   │                               │                 │
│   │  Application code             │                 │
│   │                               │                 │
│   │  ┌─────────────────────────┐  │                 │
│   │  │      Situs tests        │  │                 │
│   │  └────────────┬────────────┘  │                 │
│   └───────────────┼───────────────┘                 │
│                   │                                 │
│          ┌────────┼────────┐                        │
│          │        │        │                        │
│          ▼        ▼        ▼                        │
│      Payment    Kafka   Database                    │
│      Service                                         │
│          │                                           │
│          ▼                                           │
│     External API                                     │
│                                                     │
└─────────────────────────────────────────────────────┘
```

You're testing the system **where it actually runs**.

---

# Typical use cases

## Post-deployment verification

Deploy a microservice and immediately verify that its critical integrations work.

```text
Build
  ↓
Unit tests
  ↓
Deploy
  ↓
Run Situs
  ↓
PASS ───────→ Continue rollout
  │
 FAIL
  ↓
Stop / rollback
```

---

## Microservice dependency testing

Verify that one service can actually communicate with another.

For example:

```text
Order Service
     │
     ├──► Payment Service
     │
     ├──► Inventory Service
     │
     ├──► Kafka
     │
     └──► PostgreSQL
```

A Situs test can verify all of these from the same runtime environment as your application.

---

## Environment validation

Use Situs to verify environments such as:

```text
development
test
QA
staging
preview environments
ephemeral environments
production-like environments
```

The same test suite can be executed after every deployment.

---

## Smoke testing

Expose lightweight tests that verify the most important functionality before traffic reaches a deployment.

For example:

```text
✓ database reachable
✓ Kafka reachable
✓ authentication service reachable
✓ payment service reachable
✓ external provider credentials valid
```

---

## CI/CD deployment gates

Situs can be triggered over HTTP, making it easy to integrate with deployment pipelines.

```text
CI
 │
 ▼
Build
 │
 ▼
Deploy
 │
 ▼
POST /api/test-framework/suites/DeploymentVerification/run
 │
 ▼
Poll test status
 │
 ├── PASS → continue
 │
 └── FAIL → stop deployment
```

---

# Quick start

## 1. Add Situs

Gradle Kotlin DSL:

```kotlin
dependencies {
    implementation("no.kompilator:situs:2.0.0")
}
```

Optional reporting support:

```kotlin
dependencies {
    implementation("no.kompilator:plugins:2.0.0")
}
```

The plugins module includes support for:

- JUnit XML
- Open Test Reporting XML
- JSON

---

# 2. Create a test suite

A Situs suite is a normal Java class.

```java
@TestSuite(
    name = "OrderServiceIntegration",
    description = "Integration checks for the Order Service"
)
public class OrderServiceIntegration {

    @Test(name = "application-is-running")
    public void applicationIsRunning() {
        assertThat(true).isTrue();
    }
}
```

---

# Spring Boot

Situs integrates directly with Spring.

That means your test suites can use the **same Spring beans, clients and configuration as your application**.

```java
@Component
@TestSuite(name = "PaymentServiceIntegration")
public class PaymentServiceIntegration {

    private final PaymentClient paymentClient;

    public PaymentServiceIntegration(
            PaymentClient paymentClient
    ) {
        this.paymentClient = paymentClient;
    }

    @Test(
        name = "payment-service-is-reachable",
        timeout = "PT5S"
    )
    public void paymentServiceIsReachable() {

        var health = paymentClient.health();

        assertThat(health.isHealthy()).isTrue();
    }
}
```

This allows your tests to use the application's real:

```text
HTTP clients
authentication
configuration
service URLs
TLS configuration
serializers
connection pools
database configuration
message broker configuration
```

instead of recreating them in a separate test harness.

---

# Spring Boot configuration

Configure which packages Situs should scan:

```properties
testframework.scan-packages=com.example.tests
```

Spring Boot auto-configuration is enabled automatically when Situs is on the classpath.

For non-Boot Spring applications you can explicitly enable runtime tests using:

```java
@EnableRuntimeTests
```

Useful configuration:

```properties
testframework.scan-packages=com.example.tests

testframework.full-classpath-scan=false

testframework.max-stored-runs=200

testframework.reporting.enabled=true

testframework.reporting.output-dir=build/test-reports

testframework.reporting.formats=JUNIT_XML,OPEN_TEST_REPORTING_XML,JSON
```

---

# Running tests

Situs exposes an HTTP API through Spring Boot.

## List available suites

```bash
curl \
  http://localhost:8080/api/test-framework/suites
```

---

## Start a suite

```bash
curl -X POST \
  http://localhost:8080/api/test-framework/suites/PaymentServiceIntegration/run
```

Response:

```json
{
  "runId": "abc-123"
}
```

---

## Check status

```bash
curl \
  http://localhost:8080/api/test-framework/runs/abc-123/status
```

---

## Cancel a run

```bash
curl -X POST \
  http://localhost:8080/api/test-framework/runs/abc-123/cancel
```

---

# Example: testing several microservices

Consider an Order Service which depends on:

```text
Order Service
   │
   ├── Payment Service
   │
   ├── Inventory Service
   │
   ├── PostgreSQL
   │
   └── Kafka
```

You can create a deployment verification suite:

```java
@Component
@TestSuite(
    name = "DeploymentVerification",
    description = "Verify runtime dependencies"
)
public class DeploymentVerification {

    private final PaymentClient payment;
    private final InventoryClient inventory;
    private final JdbcTemplate database;

    public DeploymentVerification(
            PaymentClient payment,
            InventoryClient inventory,
            JdbcTemplate database
    ) {
        this.payment = payment;
        this.inventory = inventory;
        this.database = database;
    }

    @Test(
        name = "payment-service",
        timeout = "PT5S"
    )
    public void paymentService() {

        assertThat(
            payment.health().isHealthy()
        ).isTrue();
    }

    @Test(
        name = "inventory-service",
        timeout = "PT5S"
    )
    public void inventoryService() {

        assertThat(
            inventory.health().isHealthy()
        ).isTrue();
    }

    @Test(
        name = "database",
        timeout = "PT5S"
    )
    public void database() {

        Integer result =
            database.queryForObject(
                "SELECT 1",
                Integer.class
            );

        assertThat(result).isEqualTo(1);
    }
}
```

After deployment:

```bash
curl -X POST \
  http://order-service:8080/api/test-framework/suites/DeploymentVerification/run
```

Now the service verifies its dependencies from **inside the deployed environment**.

---

# Parameterized tests

Situs supports parameterized tests.

```java
@ParameterizedTest(
    name = "service[{index}] {0}"
)
@ValueSource(strings = {
    "payment",
    "inventory",
    "shipping"
})
public void serviceIsReachable(
        String service
) {

    assertThat(
        serviceRegistry.isHealthy(service)
    ).isTrue();
}
```

Supported sources:

```text
@ValueSource
@CsvSource
@CsvFileSource
@MethodSource
@EnumSource

@NullSource
@EmptySource
@NullAndEmptySource
```

Each generated invocation is treated as a separate test case for:

- execution
- reporting
- discovery
- HTTP responses

---

# CSV parameterized tests

```java
@ParameterizedTest(
    name = "addition[{index}] {0}+{1}={2}"
)
@CsvSource({
    "1,2,3",
    "2,3,5",
    "40,2,42"
})
public void addition(
        int left,
        int right,
        int expected
) {

    assertThat(
        left + right
    ).isEqualTo(expected);
}
```

---

# Method sources

```java
@ParameterizedTest(
    name = "multiply[{index}] {0}*{1}={2}"
)
@MethodSource("cases")
public void multiply(
        int left,
        int right,
        int expected
) {

    assertThat(
        left * right
    ).isEqualTo(expected);
}

static Stream<Arguments> cases() {

    return Stream.of(
        Arguments.of(2, 3, 6),
        Arguments.of(7, 6, 42)
    );
}
```

---

# Lifecycle methods

Situs supports familiar lifecycle annotations.

```java
@BeforeAll
public void beforeSuite() {
}

@BeforeEach
public void beforeTest() {
}

@Test
public void test() {
}

@AfterEach
public void afterTest() {
}

@AfterAll
public void afterSuite() {
}
```

---

# Parallel execution

Suites can execute tests in parallel.

```java
@TestSuite(
    name = "DependencyChecks",
    parallel = true
)
public class DependencyChecks {

    @Test
    public void payment() {
    }

    @Test
    public void inventory() {
    }

    @Test
    public void shipping() {
    }
}
```

This is useful when validating multiple independent microservice dependencies.

---

# Timeouts

Tests can use millisecond or ISO-8601 duration timeouts.

```java
@Test(
    name = "fast-check",
    timeoutMs = 500
)
public void fastCheck() {
}
```

Or:

```java
@Test(
    name = "external-api",
    timeout = "PT30S"
)
public void externalApi() {
}
```

Examples:

```text
PT0.5S = 500 ms
PT30S  = 30 seconds
PT5M   = 5 minutes
PT1H   = 1 hour
```

---

# Retries

Transient failures can automatically be retried.

```java
@Test(
    name = "external-service",
    retries = 2
)
public void externalService() {

    assertThat(
        client.health()
    ).isTrue();
}
```

Retries are useful for dependencies which may take a short time to become ready during deployment.

---

# Delays

Tests can wait before execution.

```java
@Test(
    name = "service-ready",
    delayMs = 500
)
public void serviceReady() {
}
```

---

# Deterministic ordering

Tests can explicitly define execution order.

```java
@Test(
    name = "create-order",
    order = 1
)
public void createOrder() {
}

@Test(
    name = "verify-order",
    order = 2
)
public void verifyOrder() {
}

@Test(
    name = "delete-order",
    order = 3
)
public void deleteOrder() {
}
```

Tests with lower `order` values run first.

Ties are resolved using the method name.

---

# Reporting

Add the reporting plugin:

```kotlin
dependencies {
    implementation("no.kompilator:plugins:2.0.0")
}
```

Configure output:

```properties
testframework.reporting.enabled=true

testframework.reporting.output-dir=build/test-reports

testframework.reporting.formats=
JUNIT_XML,
OPEN_TEST_REPORTING_XML,
JSON
```

Supported formats:

```text
JUnit XML
Open Test Reporting XML
JSON
```

Reports are automatically generated after suite execution.

---

# Programmatic reporting

Reporting plugins can also be created manually.

```java
ReportingPlugin reporter =
    ReportingPlugin.builder()
        .outputDir(
            Path.of("build/test-reports")
        )
        .format(
            ReportFormat.JUNIT_XML
        )
        .format(
            ReportFormat.JSON
        )
        .build();

testFrameworkService.addListener(
    reporter
);
```

Plugins can observe test progress using:

```java
SuiteRunListener#onTestCompleted(...)
```

---

# Runtime status

Asynchronous execution exposes runtime progress information.

For example:

```text
completedCount
totalCount

runStartedAtEpochMs
lastUpdatedAtEpochMs

startedAtEpochMs
completedAtEpochMs
```

Runs can finish with statuses such as:

```text
COMPLETED
CANCELLED
```

---

# Kotlin

Situs works with Kotlin.

```kotlin
@Component
@TestSuite(
    name = "PaymentServiceIntegration"
)
class PaymentServiceIntegration(
    private val paymentClient: PaymentClient
) {

    @Test(
        name = "payment-service-is-reachable"
    )
    fun paymentServiceIsReachable() {

        assertThat(
            paymentClient.health().isHealthy
        ).isTrue()
    }
}
```

When using Spring, use:

```kotlin
kotlin("plugin.spring")
```

so Spring-managed classes can be proxied correctly.

---

# Situs vs traditional integration tests

Situs is not intended to replace normal unit or integration tests.

It fills a different part of the testing lifecycle.

| Capability | Traditional CI integration tests | Situs |
|---|---:|---:|
| Unit-level testing | ✓ | — |
| Runs during build | ✓ | Optional |
| Runs after deployment | Usually not | ✓ |
| Uses deployed service configuration | Usually not | ✓ |
| Uses real service discovery | Limited | ✓ |
| Tests runtime networking | Limited | ✓ |
| Tests environment secrets/configuration | Limited | ✓ |
| Tests real microservice connectivity | Limited | ✓ |
| Trigger tests over HTTP | Usually not | ✓ |
| Spring dependency injection | ✓ | ✓ |
| Deployment verification | Indirect | ✓ |
| Machine-readable reports | ✓ | ✓ |

A typical setup might therefore look like:

```text
               TESTING PIPELINE

                  Source code
                       │
                       ▼
                  Unit tests
                       │
                       ▼
             Integration tests
                       │
                       ▼
                    Build
                       │
                       ▼
                    Deploy
                       │
                       ▼
                 Situs tests
                       │
                       ▼
              Runtime verified
```

---

# Situs and Testcontainers

Testcontainers is excellent for creating temporary infrastructure during automated tests.

For example:

```text
JUnit
  │
  ├── temporary PostgreSQL
  ├── temporary Kafka
  └── temporary Redis
```

Situs solves a different problem.

```text
Deployed application
      │
      ├── real PostgreSQL
      ├── real Kafka
      ├── real service discovery
      ├── real authentication
      └── real microservices
```

Testcontainers answers:

> Does my application work with these dependencies in a controlled test environment?

Situs answers:

> Does my deployed application actually work with the dependencies configured in this environment?

The two approaches can complement each other.

---

# Kubernetes example

A typical Kubernetes deployment might look like:

```text
┌──────────────── Kubernetes ─────────────────┐
│                                             │
│  ┌──────────────┐                           │
│  │ Order Service│                           │
│  │              │                           │
│  │    Situs     │                           │
│  └──────┬───────┘                           │
│         │                                   │
│   ┌─────┼──────────────┐                    │
│   │     │              │                    │
│   ▼     ▼              ▼                    │
│ Payment Inventory    Kafka                  │
│ Service Service                             │
│                                             │
└─────────────────────────────────────────────┘
```

After the deployment becomes ready:

```bash
curl -X POST \
  http://order-service/api/test-framework/suites/DeploymentVerification/run
```

The result can then be used by your deployment pipeline to determine whether rollout should continue.

---

# Features

| Feature | Situs |
|---|---|
| Test suites | `@TestSuite` |
| Tests | `@Test` |
| Parameterized tests | `@ParameterizedTest` |
| Setup / teardown | `@BeforeAll`, `@BeforeEach`, `@AfterEach`, `@AfterAll` |
| Parallel execution | ✓ |
| Deterministic ordering | ✓ |
| Timeouts | ✓ |
| Delays | ✓ |
| Retries | ✓ |
| Spring dependency injection | ✓ |
| Package-based discovery | ✓ |
| Runtime HTTP API | ✓ |
| Run cancellation | ✓ |
| JUnit XML reports | ✓ |
| Open Test Reporting XML | ✓ |
| JSON reports | ✓ |
| Java | ✓ |
| Kotlin | ✓ |

---

# Repository structure

```text
.
├── situs/
│   Core runtime testing library
│
├── plugins/
│   Reporting plugins
│
├── java-spring-boot-sample-app/
│   Java Spring Boot example
│
└── kotlin-spring-boot-sample-app/
    Kotlin Spring Boot example
```

| Module | Artifact | Description |
|---|---|---|
| `situs` | `no.kompilator:situs` | Runtime test engine, annotations, Spring integration and HTTP API |
| `plugins` | `no.kompilator:plugins` | JUnit XML, OTR XML and JSON reporting |
| `java-spring-boot-sample-app` | — | Java example |
| `kotlin-spring-boot-sample-app` | — | Kotlin example |

Published artifacts:

- [`no.kompilator:situs`](https://central.sonatype.com/artifact/no.kompilator/situs)
- [`no.kompilator:plugins`](https://central.sonatype.com/artifact/no.kompilator/plugins)

---

# Supported API

Supported packages:

```text
no.kompilator.situs.annotations
no.kompilator.situs.model
no.kompilator.situs.params
no.kompilator.situs.plugin
no.kompilator.situs.service
no.kompilator.situs.spring
no.kompilator.situs.spring.model
```

Internal packages may change without notice:

```text
no.kompilator.situs.domain
no.kompilator.situs.runtime
```

Applications should build against supported packages only.

---

# Validation

Situs fails fast during suite registration when it detects invalid configuration.

Examples include:

- duplicate suite names
- duplicate test names
- invalid timeouts
- negative delays
- negative retry counts
- invalid lifecycle methods
- static test methods
- parameterized tests without argument sources
- invalid parameter sources

This helps configuration errors appear at application startup instead of during test execution.

---

# Building

Build everything:

```bash
./situs/gradlew --project-dir . build
```

Build the core library:

```bash
./situs/gradlew :situs:build
```

Build plugins:

```bash
./situs/gradlew :plugins:build
```

---

# Running the project tests

```bash
./situs/gradlew :situs:test
```

```bash
./situs/gradlew :plugins:test
```

Or run verification across modules:

```bash
./situs/gradlew testAll
```

```bash
./situs/gradlew buildAll
```

---

# Publishing locally

```bash
./situs/gradlew publishAllToMavenLocal
```

---

# Release

Publishing to Maven Central requires credentials and signing configuration.

Configure:

```properties
centralUsername=...
centralPassword=...

signingKey=...
signingPassword=...
```

Or environment variables:

```bash
export CENTRAL_USERNAME=...
export CENTRAL_PASSWORD=...

export SIGNING_KEY=...
export SIGNING_PASSWORD=...
```

Publish:

```bash
./situs/gradlew publishRelease
```

Available release tasks:

```text
releaseCheck
publishAllToMavenLocal
publishRelease
```

GitHub Actions releases can be triggered by pushing a version tag:

```bash
git tag v2.0.0
git push origin v2.0.0
```

---

# Requirements

| Requirement | Version |
|---|---|
| Java | 21 |
| Gradle | Wrapper included |
| Spring Boot | 4.0.x optional |

The core Situs execution engine does not require Spring.

---

# Further reading

- [`situs/README.md`](situs/README.md) — annotations, runtime engine and API reference
- [`plugins/README.md`](plugins/README.md) — reporting
- [`java-spring-boot-sample-app`](java-spring-boot-sample-app/) — Java example
- [`kotlin-spring-boot-sample-app`](kotlin-spring-boot-sample-app/) — Kotlin example

---

# The idea

Modern microservices are tested extensively before deployment.

But deployment itself introduces another layer of failure:

```text
Code
+
Configuration
+
Infrastructure
+
Networking
+
Authentication
+
Other services
=
The actual running system
```

Situs gives the running application a way to verify those assumptions.

**Test your microservices where they actually run.**