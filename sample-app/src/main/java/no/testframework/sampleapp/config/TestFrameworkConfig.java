package no.testframework.sampleapp.config;

import no.testframework.javalibrary.api.TestFrameworkController;
import no.testframework.javalibrary.api.service.TestFrameworkService;
import no.testframework.sampleapp.tests.CalculatorTestSuite;
import no.testframework.sampleapp.tests.LongRunningTestSuite;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * Wires the test-framework library into the sample-app Spring context.
 *
 * <ul>
 *   <li>Declares which runtime test suite classes are available.</li>
 *   <li>Creates the {@link TestFrameworkService} with those suites.</li>
 *   <li>Creates the {@link TestFrameworkController} (it will be picked up
 *       by Spring MVC automatically because it is a {@code @RestController}).</li>
 * </ul>
 */
@Configuration
public class TestFrameworkConfig {

    /**
     * The set of runtime test suite classes that the framework will discover.
     * Add new suite classes here to register them.
     */
    @Bean
    public Set<Class<?>> registeredTestSuites() {
        return Set.of(
                CalculatorTestSuite.class,
                LongRunningTestSuite.class
        );
    }

    @Bean
    public TestFrameworkService testFrameworkService(Set<Class<?>> registeredTestSuites) {
        return new TestFrameworkService(registeredTestSuites);
    }

    @Bean
    public TestFrameworkController testFrameworkController(TestFrameworkService testFrameworkService) {
        return new TestFrameworkController(testFrameworkService);
    }
}
