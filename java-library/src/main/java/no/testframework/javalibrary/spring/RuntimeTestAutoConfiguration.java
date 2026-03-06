package no.testframework.javalibrary.spring;

import no.testframework.javalibrary.plugin.PluginRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring {@code @Configuration} that registers the test-framework beans into
 * the application context.
 *
 * <p>Activated in two ways:
 * <ol>
 *   <li><b>Spring Boot auto-configuration</b> — listed in
 *       {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 *       (Boot 2.7+) and {@code META-INF/spring.factories} (Boot 2.6 and earlier).
 *       Zero configuration needed — just add the JAR to the classpath.</li>
 *   <li><b>Explicit opt-in</b> — annotate any {@code @Configuration} class or
 *       {@code @SpringBootApplication} class with
 *       {@link EnableRuntimeTests @EnableRuntimeTests}.</li>
 * </ol>
 *
 * <p>Beans registered:
 * <ul>
 *   <li>{@link TestFrameworkService} — scans the entire classpath for
 *       {@code @RuntimeTestSuite} classes automatically.</li>
 *   <li>{@link TestFrameworkController} — exposes the REST API under
 *       {@code /api/test-framework/...}.</li>
 *   <li>{@link PluginRunner} — discovers all {@link no.testframework.javalibrary.plugin.TestFrameworkPlugin}
 *       beans in the context and calls {@code onStartup()} on those that opt in.</li>
 * </ul>
 *
 * @see EnableRuntimeTests
 * @see TestFrameworkService
 * @see TestFrameworkController
 * @see SpringInstanceFactory
 */
@Configuration
public class RuntimeTestAutoConfiguration {

    /**
     * Creates a {@link TestFrameworkService} that discovers all {@code @RuntimeTestSuite}
     * classes on the classpath automatically and wires Spring DI into suite instances
     * via {@link SpringInstanceFactory}.
     *
     * @param applicationContext the Spring application context, injected automatically
     * @return the fully initialised service
     */
    @Bean
    public TestFrameworkService testFrameworkService(ApplicationContext applicationContext) {
        return new TestFrameworkService(applicationContext);
    }

    /**
     * Creates the {@link TestFrameworkController} that exposes the REST endpoints.
     *
     * @param testFrameworkService the service bean
     * @return the controller
     */
    @Bean
    public TestFrameworkController testFrameworkController(TestFrameworkService testFrameworkService) {
        return new TestFrameworkController(testFrameworkService);
    }

    /**
     * Creates the {@link PluginRunner} that discovers all
     * {@link no.testframework.javalibrary.plugin.TestFrameworkPlugin} beans in the
     * context and calls {@code onStartup()} on those that opt in via
     * {@code runOnStartup() == true}.
     *
     * @param applicationContext the Spring application context
     * @return the plugin runner
     */
    @Bean
    public PluginRunner pluginRunner(ApplicationContext applicationContext) {
        return new PluginRunner(applicationContext);
    }
}
