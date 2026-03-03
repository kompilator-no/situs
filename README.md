# Test Framework

This repository is organized as a multi-language workspace.

## Language libraries

- `java-library`: Java implementation and domain models.

Additional language-specific libraries can be added as sibling folders (for example `python-library`, `js-library`, and more).

## Build from repository root

This repo includes a root Gradle launcher that delegates to the Java library module.

```bash
./gradlew build
```
