# Java Library (MVP)

This module provides Java domain objects and an execution runtime for defining and running test-framework suites.

## Java version

The library is configured to use Java 21.

## Domain objects

- `TestSuite`: top-level suite metadata and ordered steps.
- `TestStep`: a single test step with actions and validators.
- `TestAction`: a generic action descriptor (`type`, `target`, and dynamic parameters).
- `TestValidator`: a generic validator descriptor (`type`, `target`, and expected values).

## Runtime objects

- `TestSuiteRunner`: executes suites with pluggable action/validator handlers.
- `TestExecutionContext`: key/value state shared across actions and validators.
- `TestRuntimeConfiguration`: fluent registration for handlers and runner creation.
- `TestSuiteResult`/`TestStepResult`: immutable execution report objects.

## Included features

- Constructor validation for required fields (`id`, `name`, `type`, `target`, and list containers).
- Defensive immutable copies of all lists/maps.
- Overloaded constructors for convenient defaults on optional collections and description values.
- Convenience methods for incremental construction and immutable updates:
  - `TestSuite.withStep(...)`
  - `TestStep.withAction(...)`
  - `TestStep.withValidator(...)`
  - `TestAction.withParameter(...)`
  - `TestValidator.withExpected(...)`
- Runtime-safe suite execution with:
  - handler lookup by action/validator `type`
  - fail-fast behavior (suite stops on first failing step)
  - execution context snapshot for later reporting

## Spring Boot quick-start

You can wire this directly in a Spring Boot app by registering handlers as beans and building a runner:

```java
@Configuration
class TestFrameworkConfig {

    @Bean
    TestSuiteRunner testSuiteRunner(MyGateway gateway) {
        return new TestRuntimeConfiguration()
                .registerActionHandler("httpGet", (action, context) -> {
                    String url = (String) action.parameters().get("url");
                    String body = gateway.get(url);
                    context.put("lastBody", body);
                })
                .registerValidatorHandler("bodyContains", (validator, context) -> {
                    String expected = (String) validator.expected().get("value");
                    String body = context.get("lastBody", String.class);
                    if (body == null || !body.contains(expected)) {
                        throw new IllegalStateException("Response body did not contain expected value");
                    }
                })
                .buildRunner();
    }
}
```

Then call `runner.run(suite)` from your service layer.

## API helpers for apps

The library now includes app-facing helpers in `no.testframework.javalibrary.api`:

- `TestFrameworkApi`: a small facade for running suites from application code.
- `TestFrameworkApiHandlers`: predefined handlers (`setContext` and `contextEquals`) that can be reused in APIs.
- `TestFrameworkController`: a reusable Spring `@RestController` exposing:
  - `GET /api/test-framework/status`
  - `GET /api/test-framework/tests`
  - `POST /api/test-framework/suites/run`

Example Spring Boot wiring:

```java
@Configuration
class TestFrameworkApiConfig {

    @Bean
    TestFrameworkApi testFrameworkApi() {
        return TestFrameworkApi.withDefaults();
    }

    @Bean
    TestFrameworkController testFrameworkController(TestFrameworkApi api) {
        return new TestFrameworkController(api);
    }
}
```

## Build

From repository root:

```bash
./java-library/gradlew -p java-library build
```

## Test

From repository root:

```bash
./java-library/gradlew -p java-library test
```
