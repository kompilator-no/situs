# Java, Kotlin, and Spring Boot Instructions

This repository contains both Java and Kotlin code, with Spring Boot used for service wiring and runtime configuration.

## Java guidelines
- Use Java for Spring Boot integration points and shared runner library APIs where existing code already follows Java conventions.
- Keep classes small and focused; prefer constructor injection for Spring-managed components.
- Use immutable request/response models when practical.
- Keep package naming under `no.testframework.*`.

## Kotlin guidelines
- Use Kotlin for framework and transport modules.
- Prefer data classes and immutable values (`val`) by default.
- Keep nullability explicit and avoid platform-type ambiguity.
- Use extension functions and DSL-style builders only when they improve readability.

## Spring Boot guidelines
- Keep HTTP API contracts stable and documented in code.
- Use `@ConfigurationProperties` for typed config and keep defaults in `application.yaml`.
- Favor constructor injection over field injection.
- Keep controller logic thin; delegate orchestration to service classes.
- Validate input at API boundaries and return clear error payloads.

## Testing and quality
- Add or update unit tests for behavior changes.
- Add integration tests when changing Spring Boot API or configuration behavior.
- Keep Gradle and Maven build definitions aligned when changing shared dependencies.

## Build and run
- Gradle multi-module build: `./gradlew build`
- Spring Boot app run: `./gradlew bootRun`
- Maven compatibility check (if needed): `mvn test`
