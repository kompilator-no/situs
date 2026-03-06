package no.testframework.plugins.reporting;

import no.testframework.javalibrary.domain.TestSuiteExecutionResult;
import no.testframework.javalibrary.plugin.SuiteRunListener;
import no.testframework.javalibrary.plugin.TestFrameworkPlugin;
import no.testframework.javalibrary.runtime.InstanceFactory;
import no.testframework.javalibrary.runtime.RuntimeTestSuiteRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link TestFrameworkPlugin} that runs one or more test suite classes and writes
 * structured report files after each run.
 *
 * <p>Supported output formats are defined by {@link ReportFormat}:
 * <ul>
 *   <li>{@link ReportFormat#JUNIT_XML} — Surefire XML (CI-universal)</li>
 *   <li>{@link ReportFormat#OPEN_TEST_REPORTING_XML} — Open Test Reporting XML</li>
 *   <li>{@link ReportFormat#JSON} — simple JSON</li>
 * </ul>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * ReportingPlugin reporter = ReportingPlugin.builder()
 *         .suite(CalculatorTestSuite.class)
 *         .suite(PaymentTestSuite.class)
 *         .outputDir(Path.of("build/test-reports"))
 *         .format(ReportFormat.JUNIT_XML)
 *         .format(ReportFormat.OPEN_TEST_REPORTING_XML)
 *         .build();
 *
 * reporter.runAndReport();
 * }</pre>
 *
 * <h2>Spring bean</h2>
 * <pre>{@code
 * @Bean
 * public ReportingPlugin reportingPlugin() {
 *     return ReportingPlugin.builder()
 *             .suite(CalculatorTestSuite.class)
 *             .outputDir(Path.of("build/test-reports"))
 *             .format(ReportFormat.JUNIT_XML)
 *             .format(ReportFormat.OPEN_TEST_REPORTING_XML)
 *             .format(ReportFormat.JSON)
 *             .build();
 * }
 * }</pre>
 *
 * <p>Report files are written to the configured {@code outputDir} (default:
 * {@code build/test-reports}). File names are derived from the suite name — see
 * {@link ReportFormat} for the exact naming scheme per format.
 *
 * @see SuiteReportWriter
 * @see ReportFormat
 */
public class ReportingPlugin implements TestFrameworkPlugin, SuiteRunListener {

    private static final Logger log = LoggerFactory.getLogger(ReportingPlugin.class);

    private final List<Class<?>> suiteClasses;
    private final SuiteReportWriter writer;
    private final RuntimeTestSuiteRunner runner;

    private ReportingPlugin(Builder builder) {
        this.suiteClasses = List.copyOf(builder.suiteClasses);
        this.writer       = builder.writer;
        this.runner       = new RuntimeTestSuiteRunner(builder.instanceFactory);
    }

    // -------------------------------------------------------------------------
    // SuiteRunListener — writes reports when tests are run
    // -------------------------------------------------------------------------

    /**
     * Called automatically by {@link no.testframework.javalibrary.spring.TestFrameworkService}
     * whenever a suite run completes. Writes report files for the result.
     *
     * <p>This is the primary way reports are produced — no manual invocation needed.
     * Register this plugin as a listener via
     * {@code testFrameworkService.addListener(reportingPlugin)} or let
     * {@link ReportingAutoConfiguration} wire it automatically.
     *
     * @param result the completed suite result
     */
    @Override
    public void onSuiteCompleted(TestSuiteExecutionResult result) {
        try {
            writer.write(result);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to write report for suite: " + result.getSuiteName(), e);
        }
    }

    // -------------------------------------------------------------------------
    // TestFrameworkPlugin
    // -------------------------------------------------------------------------

    /** @return {@code "ReportingPlugin"} */
    @Override
    public String pluginName() {
        return "ReportingPlugin";
    }


    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Runs all configured suite classes and writes a report for each one.
     *
     * <p>All suites are run regardless of whether earlier ones fail — every suite
     * gets its own report file.
     *
     * @return the list of results produced (one per suite class, in registration order)
     * @throws UncheckedIOException if a report file cannot be written
     */
    public List<TestSuiteExecutionResult> runAndReport() {
        List<TestSuiteExecutionResult> results = new ArrayList<>();
        for (Class<?> suiteClass : suiteClasses) {
            log.info("Running suite: {}", suiteClass.getSimpleName());
            TestSuiteExecutionResult result = runner.runSuite(suiteClass);
            results.add(result);
            try {
                writer.write(result);
            } catch (IOException e) {
                throw new UncheckedIOException(
                        "Failed to write report for suite: " + result.getSuiteName(), e);
            }
        }
        return results;
    }

    /**
     * Returns the suite classes registered with this plugin.
     *
     * @return an unmodifiable list of suite classes
     */
    public List<Class<?>> getSuiteClasses() {
        return suiteClasses;
    }

    /**
     * Returns the {@link SuiteReportWriter} used by this plugin.
     *
     * @return the report writer
     */
    public SuiteReportWriter getWriter() {
        return writer;
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Returns a new builder for {@link ReportingPlugin}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link ReportingPlugin}.
     */
    public static final class Builder {

        private final List<Class<?>> suiteClasses = new ArrayList<>();
        private Path outputDir = Path.of("build/test-reports");
        private final List<ReportFormat> formats  = new ArrayList<>();
        private InstanceFactory instanceFactory   = InstanceFactory.reflective();

        private Builder() {}

        /**
         * Adds a test suite class to run and report on.
         *
         * @param suiteClass a class annotated with {@code @RuntimeTestSuite}
         * @return this builder
         */
        public Builder suite(Class<?> suiteClass) {
            this.suiteClasses.add(suiteClass);
            return this;
        }

        /**
         * Sets the directory where report files are written.
         * Defaults to {@code build/test-reports}.
         *
         * @param outputDir the output directory
         * @return this builder
         */
        public Builder outputDir(Path outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        /**
         * Adds a report format to produce after each suite run.
         *
         * @param format the format to add
         * @return this builder
         */
        public Builder format(ReportFormat format) {
            this.formats.add(format);
            return this;
        }

        /**
         * Sets the {@link InstanceFactory} used to create suite class instances.
         *
         * <p>Defaults to {@link InstanceFactory#reflective()} (plain no-arg constructor).
         * Pass a {@code SpringInstanceFactory} when running inside a Spring context so
         * that suite classes annotated with {@code @Component} receive their dependencies
         * via injection rather than failing with {@code NoSuchMethodException}.
         *
         * <pre>{@code
         * ReportingPlugin.builder()
         *         .suite(CalculatorTestSuite.class)
         *         .instanceFactory(new SpringInstanceFactory(applicationContext))
         *         .format(ReportFormat.JUNIT_XML)
         *         .build();
         * }</pre>
         *
         * @param instanceFactory the factory to use
         * @return this builder
         */
        public Builder instanceFactory(InstanceFactory instanceFactory) {
            this.instanceFactory = instanceFactory;
            return this;
        }

        /**
         * Builds and returns the configured {@link ReportingPlugin}.
         *
         * @return a new {@code ReportingPlugin}
         * @throws IllegalStateException if no suite classes or no formats have been configured
         */
        public ReportingPlugin build() {
            if (suiteClasses.isEmpty()) {
                throw new IllegalStateException("At least one suite class must be added via suite(...)");
            }
            if (formats.isEmpty()) {
                throw new IllegalStateException("At least one ReportFormat must be added via format(...)");
            }
            SuiteReportWriter.Builder writerBuilder = SuiteReportWriter.builder().outputDir(outputDir);
            formats.forEach(writerBuilder::format);
            this.writer = writerBuilder.build();
            return new ReportingPlugin(this);
        }

        private SuiteReportWriter writer;
    }
}
