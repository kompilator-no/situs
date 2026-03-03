# Java Library (MVP)

This module provides a test framework with two styles:

1. Domain/runtime model (`TestSuite`, `TestStep`, `TestAction`, `TestValidator`).
2. Preferred object-oriented model in `no.testframework.javalibrary.suite`.

## Java version

Java 21.

## Object-oriented API (preferred)

Main types:

- `TestSuite`
- `TestCase`
- `Step`
- `SuiteApi`
- `SuiteRunner`
- `ExecutionStrategy`
- `StepExecutionCondition`

HTTP primitives for OO suites:

- `HttpEndpoints`
- `HttpEndpoint`
- `HttpBody`

Example:

```java
public final class AdvancedTestSuite implements TestSuite {
    @Override
    public String name() {
        return "Test Suite Name";
    }

    @Override
    public List<TestCase> testCases() {
        return List.of(new MyHttpTestCase(), new MyKafkaTestCase());
    }
}
```

Run:

```java
SuiteApi api = SuiteApi.create();
SuiteResult result = api.runSuite(new AdvancedTestSuite());
```


## ActionStep

`ActionStep` is an OO `Step` that encapsulates an `Action` with optional delay/timeout and lifecycle hooks.

You can create it in two ways:

1. Interface implementation (`implements ActionStep` and provide `action()`).
2. Builder (`ActionStep.builder(...)`).

Builder example:

```java
ActionStep actionStep = ActionStep
        .builder(context -> context.put("state", "ok"))
        .name("Action step name")
        .description("Action step description")
        .delay(Delay.from(10, TimeUnit.SECONDS))
        .timeout(Timeout.from(1, TimeUnit.MINUTES))
        .onStarted(() -> { /* log */ })
        .onFinished(() -> { /* log */ })
        .build();
```


## ValidatorStep

`ValidatorStep` is an OO `Step` that encapsulates a `Validator` with optional delay/timeout and lifecycle hooks.

You can create it in two ways:

1. Interface implementation (`implements ValidatorStep` and provide `validator()`).
2. Builder (`ValidatorStep.builder(...)`).

Builder example:

```java
ValidatorStep validatorStep = ValidatorStep
        .builder(context -> "ok".equals(context.get("state", String.class)))
        .name("Validator step name")
        .description("Validator step description")
        .delay(Delay.from(10, TimeUnit.SECONDS))
        .timeout(Timeout.from(1, TimeUnit.MINUTES))
        .onStarted(() -> { /* log */ })
        .onFinished(() -> { /* log */ })
        .build();
```


## GenericAction

`GenericAction` is a less-opinionated action abstraction intended for custom code execution.

Create it by:

1. interface implementation (`implements GenericAction`), or
2. builder (`GenericAction.builder(...)`).

Builder example:

```java
GenericAction genericAction = GenericAction
        .builder(() -> Optional.empty())
        .iterations(Iterations.from(1))
        .onStarted(context -> { /* use context.registry() */ })
        .onFinished(() -> { /* log */ })
        .onException(exception -> { /* handle */ })
        .build();
```

Use it inside a step with:

```java
Step step = ActionStep.from(genericAction);
```

## GenericValidator

`GenericValidator` allows custom validation logic with retries/attempts.

Create it by:

1. interface implementation (`implements GenericValidator`), or
2. builder (`GenericValidator.builder(...)`).

Builder example:

```java
GenericValidator genericValidator = GenericValidator
        .builder(() -> Assertion.success())
        .attempts(Attempts.from(1))
        .onStarted(context -> { /* use context.registry() */ })
        .onFinished(() -> { /* log */ })
        .onException(exception -> { /* handle */ })
        .build();
```

Use it inside a step with:

```java
Step step = ValidatorStep.from(genericValidator);
```

## App-facing handlers

`TestFrameworkApiHandlers` now provides generic non-HTTP handlers:

- `setContext`
- `contextEquals`
- `customAction`
- `customValidator`

HTTP action/validator are isolated in their own package:

- package: `no.testframework.javalibrary.api.http`
- class: `HttpHandlers`
  - `ACTION_HTTP_REQUEST`
  - `VALIDATOR_HTTP_STATUS_EQUALS`
  - `CONTEXT_LAST_HTTP_RESPONSE`
- class: `HttpResponseData`

`TestFrameworkApi.withDefaults()` automatically registers both base handlers and HTTP handlers.

## Build

```bash
./java-library/gradlew -p java-library build
```

## Test

```bash
./java-library/gradlew -p java-library test
```
