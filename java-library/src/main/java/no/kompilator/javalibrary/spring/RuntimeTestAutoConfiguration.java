package no.kompilator.javalibrary.spring;

import no.kompilator.javalibrary.plugin.PluginRunner;
import no.kompilator.javalibrary.runtime.ClasspathScanner;
import no.kompilator.javalibrary.service.TestFrameworkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;
import java.util.Set;

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
 *   <li>{@link TestFrameworkService} — discovers {@code @TestSuite} classes from
 *       configured scan packages, or from the application's auto-configuration base
 *       packages by default in Spring Boot.</li>
 *   <li>{@link TestFrameworkController} — exposes the REST API under
 *       {@code /api/test-framework/...}.</li>
 *   <li>{@link PluginRunner} — discovers all {@link no.kompilator.javalibrary.plugin.TestFrameworkPlugin}
 *       beans in the context and calls {@code onStartup()} on those that opt in.</li>
 * </ul>
 *
 * @see EnableRuntimeTests
 * @see TestFrameworkService
 * @see TestFrameworkController
 * @see SpringInstanceFactory
 * @see TestFrameworkProperties
 */
@Configuration
@EnableConfigurationProperties(TestFrameworkProperties.class)
public class RuntimeTestAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RuntimeTestAutoConfiguration.class);

    /**
     * Creates a {@link TestFrameworkService} that discovers {@code @TestSuite}
     * classes using package-scoped scanning by default and wires Spring DI into suite
     * instances via {@link SpringInstanceFactory}.
     *
     * @param applicationContext the Spring application context, injected automatically
     * @param beanFactory the bean factory used to resolve Spring Boot base packages
     * @param properties runtime test framework configuration properties
     * @return the fully initialised service
     */
    @Bean
    @ConditionalOnMissingBean(TestFrameworkService.class)
    public TestFrameworkService testFrameworkService(
            ApplicationContext applicationContext,
            BeanFactory beanFactory,
            TestFrameworkProperties properties) {
        return createService(applicationContext, beanFactory, properties);
    }

    /**
     * Creates the {@link TestFrameworkController} that exposes the REST endpoints.
     *
     * @param testFrameworkService the service bean
     * @return the controller
     */
    @Bean
    @ConditionalOnMissingBean(TestFrameworkController.class)
    public TestFrameworkController testFrameworkController(TestFrameworkService testFrameworkService) {
        return new TestFrameworkController(testFrameworkService);
    }

    /**
     * Creates the {@link PluginRunner} that discovers all
     * {@link no.kompilator.javalibrary.plugin.TestFrameworkPlugin} beans in the
     * context and calls {@code onStartup()} on those that opt in via
     * {@code runOnStartup() == true}.
     *
     * @param applicationContext the Spring application context
     * @return the plugin runner
     */
    @Bean
    @ConditionalOnMissingBean(PluginRunner.class)
    public PluginRunner pluginRunner(ApplicationContext applicationContext) {
        return new PluginRunner(applicationContext);
    }

    private TestFrameworkService createService(
            ApplicationContext applicationContext,
            BeanFactory beanFactory,
            TestFrameworkProperties properties) {
        SpringInstanceFactory instanceFactory = new SpringInstanceFactory(applicationContext);

        if (properties.isFullClasspathScan()) {
            log.warn("Full classpath scanning is enabled via 'testframework.full-classpath-scan=true'");
            return new TestFrameworkService(
                    instanceFactory,
                    ClasspathScanner.findAllTestSuites(),
                    properties.getMaxStoredRuns());
        }

        Set<String> scanPackages = resolveScanPackages(beanFactory, properties);
        if (!scanPackages.isEmpty()) {
            log.info("Runtime test discovery scanning packages: {}", scanPackages);
            return new TestFrameworkService(instanceFactory, findSuites(scanPackages), properties.getMaxStoredRuns());
        }

        log.warn("No scan packages configured or inferred; falling back to full classpath scan");
        return new TestFrameworkService(
                instanceFactory,
                ClasspathScanner.findAllTestSuites(),
                properties.getMaxStoredRuns());
    }

    private Set<String> resolveScanPackages(BeanFactory beanFactory, TestFrameworkProperties properties) {
        Set<String> configuredPackages = properties.getScanPackages().stream()
                .map(String::trim)
                .filter(pkg -> !pkg.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!configuredPackages.isEmpty()) {
            return configuredPackages;
        }

        if (AutoConfigurationPackages.has(beanFactory)) {
            return new LinkedHashSet<>(AutoConfigurationPackages.get(beanFactory));
        }

        return Set.of();
    }

    private Set<Class<?>> findSuites(Set<String> scanPackages) {
        Set<Class<?>> suites = new LinkedHashSet<>();
        for (String scanPackage : scanPackages) {
            suites.addAll(ClasspathScanner.findTestSuites(scanPackage));
        }
        return suites;
    }
}
