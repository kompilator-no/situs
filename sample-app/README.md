# Sample Spring Boot App

This sample app demonstrates the **suite** test API from `java-library`.

## What it includes

- `TestFrameworkSampleConfig`: registers `SuiteApi`.
- `SampleSuiteDefinition`: class-based suite with explicit `TestCase` and `Step` implementations (HTTP, Kafka, and GenericAction/GenericValidator examples).
- `SampleSuiteService`: runs the sample suite.
- `SampleSuiteServiceTest`: verifies suite execution and context values.


The sample suite demonstrates:

- builder-based ActionStep and builder-based ValidatorStep in the HTTP test case,
- interface-based ActionStep and interface-based ValidatorStep implementations in the Kafka test case,
- GenericAction + GenericValidator via `ActionStep.from(...)` and `ValidatorStep.from(...)` in a dedicated generic test case.

## Run from service

```java
SuiteResult result = sampleSuiteService.runSampleSuite();
```

## Run tests

```bash
./gradlew :sample-app:test
```
