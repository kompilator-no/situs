package no.testframework.sampleapp;

import no.testframework.javalibrary.spring.EnableRuntimeTests;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the sample Spring Boot application demonstrating the test framework.
 *
 * <h2>Framework activation</h2>
 * <p>The {@code @EnableRuntimeTests} annotation here is <b>optional</b> — the framework
 * activates automatically via Spring Boot auto-configuration as soon as the library JAR
 * is on the classpath (registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}).
 * It is included here to make the intent explicit and to serve as documentation.
 *
 * <h2>Suite discovery</h2>
 * <p>The auto-configuration scans the entire classpath for classes annotated with
 * {@code @RuntimeTestSuite}. No manual bean registration is needed — just annotate
 * a class and the framework will find it.
 *
 * <h2>Spring DI in test suites</h2>
 * <p>Test suite classes that are also Spring beans (e.g. annotated with {@code @Component})
 * receive full dependency injection via the framework's {@code SpringInstanceFactory}.
 * See {@link no.testframework.sampleapp.tests.CalculatorTestSuite} for an example.
 *
 * <h2>HTTP API</h2>
 * <p>Once running, the following endpoints are available:
 * <ul>
 *   <li>{@code GET  /api/test-framework/status}   — health check</li>
 *   <li>{@code GET  /api/test-framework/suites}   — list all discovered suites</li>
 *   <li>{@code POST /api/test-framework/suites/{name}/run} — start a suite run</li>
 *   <li>{@code GET  /api/test-framework/runs/{runId}/status} — poll run status</li>
 * </ul>
 */
@SpringBootApplication
@EnableRuntimeTests
public class SampleAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleAppApplication.class, args);
    }
}
