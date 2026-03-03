# Sample Spring Boot App

This sample app demonstrates how to use the `java-library` test framework in a Spring Boot service.

## What it includes

- `TestFrameworkSampleConfig`: registers `TestFrameworkApi` with default handlers.
- `SampleSuiteFactory`: builds a sample test suite with two steps.
- `SampleSuiteService`: runs the sample suite through the framework API.
- `SampleSuiteServiceTest`: Spring Boot test that verifies the suite and both steps pass.

## Run tests

From repository root:

```bash
./gradlew :sample-app:test
```
