package no.testframework.kotlinapp

import no.testframework.javalibrary.spring.EnableRuntimeTests
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Entry point for the Kotlin Spring Boot sample application demonstrating the test framework.
 *
 * ## Framework activation
 * `@EnableRuntimeTests` is **optional** — the framework activates automatically via
 * Spring Boot auto-configuration when the library JAR is on the classpath.
 * It is included here to make the intent explicit.
 *
 * ## Suite discovery
 * The auto-configuration scans the entire classpath for classes annotated with
 * `@RuntimeTestSuite`. No manual registration is needed.
 *
 * ## Spring DI in test suites
 * Suite classes annotated with `@Component` are resolved from the `ApplicationContext`
 * and receive constructor injection via `SpringInstanceFactory`.
 * See [no.testframework.kotlinapp.tests.CalculatorTestSuite] for an example.
 *
 * ## HTTP API
 * - `GET  /api/test-framework/status`          — health check
 * - `GET  /api/test-framework/suites`          — list all discovered suites
 * - `POST /api/test-framework/suites/{name}/run` — start a suite run
 * - `GET  /api/test-framework/runs/{runId}/status` — poll run status
 *
 * ## Note on `open` classes
 * The `kotlin.plugin.spring` Gradle plugin (applied in `build.gradle.kts`) automatically
 * makes `@Component`-annotated classes `open`, which is required for Spring proxying.
 */
@SpringBootApplication
@EnableRuntimeTests
class KotlinSampleApplication

fun main(args: Array<String>) {
    runApplication<KotlinSampleApplication>(*args)
}
