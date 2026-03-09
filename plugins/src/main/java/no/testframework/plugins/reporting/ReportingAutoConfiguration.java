package no.testframework.plugins.reporting;

import no.testframework.javalibrary.service.TestFrameworkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;

/**
 * Spring Boot auto-configuration for the {@link ReportingPlugin}.
 *
 * <p>Activated automatically when the {@code plugins} JAR is on the classpath.
 * Registers a {@link ReportingPlugin} bean that:
 * <ul>
 *   <li>Consumes suite-completed events from {@link TestFrameworkService}
 *       and writes reports without controlling execution.</li>
 *   <li>Writes all three report formats by default
 *       ({@link ReportFormat#JUNIT_XML}, {@link ReportFormat#OPEN_TEST_REPORTING_XML},
 *       {@link ReportFormat#JSON}).</li>
 * </ul>
 *
 * <h2>Minimum configuration</h2>
 * <p>No configuration is required. Add the JAR and the plugin activates automatically:
 * <pre>
 * dependencies {
 *     implementation("no.testframework:plugins:0.1.0")
 * }
 * </pre>
 *
 * <h2>Properties</h2>
 * <dl>
 *   <dt>{@code testframework.reporting.enabled} (default: {@code true})</dt>
 *   <dd>Set to {@code false} to disable the plugin entirely.</dd>
 *   <dt>{@code testframework.reporting.output-dir} (default: {@code build/test-reports})</dt>
 *   <dd>Directory where report files are written.</dd>
 *   <dt>{@code testframework.reporting.formats} (default: {@code JUNIT_XML,OPEN_TEST_REPORTING_XML,JSON})</dt>
 *   <dd>Comma-separated list of formats to produce.</dd>
 * </dl>
 *
 * <h2>Override</h2>
 * <p>Declare your own {@link ReportingPlugin} {@code @Bean} to take full control —
 * this auto-configuration backs off when a bean already exists
 * ({@code @ConditionalOnMissingBean}).
 *
 * @see ReportingPlugin
 * @see no.testframework.javalibrary.plugin.PluginRunner
 */
@AutoConfiguration
@ConditionalOnBean(TestFrameworkService.class)
public class ReportingAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ReportingAutoConfiguration.class);

    /** Creates the auto-configuration. Instantiated by the Spring Boot auto-configuration mechanism. */
    public ReportingAutoConfiguration() {}

    /**
     * Creates and registers a {@link ReportingPlugin} bean.
     *
     * <p>The plugin is registered as a {@link no.testframework.javalibrary.plugin.SuiteRunListener}
     * on the {@link TestFrameworkService} so reports are written automatically whenever
     * any suite run completes — no manual invocation needed.
     *
     * @param testFrameworkService the service to register the listener on
     * @param outputDir            directory to write report files into
     * @param formats              comma-separated list of {@link ReportFormat} names
     * @return the configured {@link ReportingPlugin}
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "testframework.reporting.enabled", havingValue = "true", matchIfMissing = true)
    public ReportingPlugin reportingPlugin(
            TestFrameworkService testFrameworkService,
            @Value("${testframework.reporting.output-dir:build/test-reports}") String outputDir,
            @Value("${testframework.reporting.formats:JUNIT_XML,OPEN_TEST_REPORTING_XML,JSON}") String formats) {
        log.info("ReportingAutoConfiguration: registering reporting listener");

        ReportingPlugin.Builder builder = ReportingPlugin.builder()
                .outputDir(Path.of(outputDir));

        for (String format : formats.split(",")) {
            builder.format(ReportFormat.valueOf(format.trim()));
        }

        ReportingPlugin plugin = builder.build();

        // Register as a listener — reports are written whenever any suite run completes
        testFrameworkService.addListener(plugin);

        return plugin;
    }
}
