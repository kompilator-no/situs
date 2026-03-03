# Test Framework

This repository is organized as a multi-module Gradle workspace.

## Modules

- `java-library`: Java implementation and domain/runtime models.
- `sample-app`: Spring Boot sample app showing how to run a test suite with steps using the library.

## Build from repository root

```bash
./gradlew build
```

## Run sample app tests

```bash
./gradlew :sample-app:test
```
