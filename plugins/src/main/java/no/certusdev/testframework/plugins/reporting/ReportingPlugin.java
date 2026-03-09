package no.certusdev.testframework.plugins.reporting;

import no.certusdev.testframework.javalibrary.model.TestSuiteResult;
import no.certusdev.testframework.javalibrary.plugin.SuiteCompletedEvent;
import no.certusdev.testframework.javalibrary.plugin.SuiteRunListener;
import no.certusdev.testframework.javalibrary.plugin.TestFrameworkPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link TestFrameworkPlugin} that writes structured report files after each suite run.
 *
 * <p>Supported output formats are defined by {@link ReportFormat}:
 * <ul>
 *   <li>{@link ReportFormat#JUNIT_XML} — Surefire XML (CI-universal)</li>
 *   <li>{@link ReportFormat#OPEN_TEST_REPORTING_XML} — Open Test Reporting XML</li>
 *   <li>{@link ReportFormat#JSON} — simple JSON</li>
 * </ul>
 *
 * <h2>Spring bean</h2>
 * <pre>{@code
 * @Bean
 * public ReportingPlugin reportingPlugin() {
 *     return ReportingPlugin.builder()
 *             .outputDir(Path.of("build/test-reports"))
 *             .format(ReportFormat.JUNIT_XML)
 *             .format(ReportFormat.OPEN_TEST_REPORTING_XML)
 *             .format(ReportFormat.JSON)
 *             .build();
 * }
 * }</pre>
 *
 * <p>The plugin does not discover or execute suites itself. It listens for
 * {@link SuiteCompletedEvent}s emitted by the main library and writes reports
 * from the public result model only.
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

    private final SuiteReportWriter writer;

    private ReportingPlugin(Builder builder) {
        this.writer = builder.writer;
    }

    // -------------------------------------------------------------------------
    // SuiteRunListener — writes reports when tests are run
    // -------------------------------------------------------------------------

    /**
     * Called automatically by {@link no.certusdev.testframework.javalibrary.service.TestFrameworkService}
     * whenever a suite run completes. Writes report files for the result.
     *
     * <p>This is the primary way reports are produced — no manual invocation needed.
     * Register this plugin as a listener via
     * {@code testFrameworkService.addListener(reportingPlugin)} or let
     * {@link ReportingAutoConfiguration} wire it automatically.
     *
     * @param event the completed suite event
     */
    @Override
    public void onSuiteCompleted(SuiteCompletedEvent event) {
        try {
            log.info("Writing reports for suite: {}", event.result().getSuiteName());
            writer.write(event.result());
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to write report for suite: " + event.result().getSuiteName(), e);
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

        private Path outputDir = Path.of("build/test-reports");
        private final List<ReportFormat> formats  = new ArrayList<>();

        private Builder() {}

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
         * Builds and returns the configured {@link ReportingPlugin}.
         *
         * @return a new {@code ReportingPlugin}
         * @throws IllegalStateException if no formats have been configured
         */
        public ReportingPlugin build() {
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
