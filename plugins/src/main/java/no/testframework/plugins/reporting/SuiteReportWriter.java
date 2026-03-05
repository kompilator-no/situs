package no.testframework.plugins.reporting;

import no.testframework.javalibrary.domain.TestCaseExecutionResult;
import no.testframework.javalibrary.domain.TestSuiteExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes a {@link TestSuiteExecutionResult} to disk in one or more structured formats.
 *
 * <p>Three formats are supported — see {@link ReportFormat} for details:
 * <ul>
 *   <li>{@link ReportFormat#JUNIT_XML} — Surefire/JUnit XML (widest CI tool support)</li>
 *   <li>{@link ReportFormat#OPEN_TEST_REPORTING_XML} — Open Test Reporting XML schema</li>
 *   <li>{@link ReportFormat#JSON} — simple JSON array</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * SuiteReportWriter writer = SuiteReportWriter.builder()
 *         .outputDir(Path.of("build/test-reports"))
 *         .format(ReportFormat.JUNIT_XML)
 *         .format(ReportFormat.OPEN_TEST_REPORTING_XML)
 *         .build();
 *
 * writer.write(suiteResult);
 * }</pre>
 *
 * @see ReportingPlugin for automatic invocation after every suite run
 */
public class SuiteReportWriter {

    private static final Logger log = LoggerFactory.getLogger(SuiteReportWriter.class);

    private static final DateTimeFormatter ISO_INSTANT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);

    private final Path outputDir;
    private final List<ReportFormat> formats;

    private SuiteReportWriter(Builder builder) {
        this.outputDir = builder.outputDir;
        this.formats   = List.copyOf(builder.formats);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Writes the given suite result to disk in all configured formats.
     *
     * @param result the suite result to write
     * @throws IOException if the output directory cannot be created or a file cannot be written
     */
    public void write(TestSuiteExecutionResult result) throws IOException {
        Files.createDirectories(outputDir);
        for (ReportFormat format : formats) {
            switch (format) {
                case JUNIT_XML              -> writeJUnitXml(result);
                case OPEN_TEST_REPORTING_XML -> writeOpenTestReportingXml(result);
                case JSON                   -> writeJson(result);
            }
        }
    }

    /**
     * Returns the output directory this writer writes reports into.
     *
     * @return the output directory path
     */
    public Path getOutputDir() {
        return outputDir;
    }

    /**
     * Returns the formats this writer will produce.
     *
     * @return an unmodifiable list of {@link ReportFormat}s
     */
    public List<ReportFormat> getFormats() {
        return formats;
    }

    // -------------------------------------------------------------------------
    // JUNIT_XML
    // -------------------------------------------------------------------------

    /**
     * Writes a Surefire/JUnit XML report.
     *
     * <p>File name: {@code TEST-{suiteName}.xml}
     *
     * <p>Schema compatible with Apache Maven Surefire, Gradle's built-in XML reporter,
     * Jenkins JUnit plugin, GitHub Actions test summaries, and IntelliJ IDEA.
     */
    private void writeJUnitXml(TestSuiteExecutionResult result) throws IOException {
        String safeName = safeName(result.getSuiteName());
        Path file = outputDir.resolve("TEST-" + safeName + ".xml");

        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            long totalMs  = result.getTestCaseResults().stream()
                    .mapToLong(TestCaseExecutionResult::getDurationMs).sum();
            double totalSec = totalMs / 1000.0;

            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            w.write(String.format(
                    "<testsuite name=\"%s\" tests=\"%d\" failures=\"%d\" errors=\"0\" "
                    + "skipped=\"0\" time=\"%.3f\" timestamp=\"%s\">\n",
                    xmlEsc(result.getSuiteName()),
                    result.getTestCaseResults().size(),
                    result.getFailedCount(),
                    totalSec,
                    ISO_INSTANT.format(Instant.now())
            ));

            for (TestCaseExecutionResult tc : result.getTestCaseResults()) {
                w.write(String.format(
                        "  <testcase name=\"%s\" classname=\"%s\" time=\"%.3f\"",
                        xmlEsc(tc.getName()),
                        xmlEsc(result.getSuiteName()),
                        tc.getDurationMs() / 1000.0
                ));

                if (tc.isPassed()) {
                    w.write("/>\n");
                } else {
                    w.write(">\n");
                    String msg   = tc.getErrorMessage()  != null ? xmlEsc(tc.getErrorMessage())  : "";
                    String type  = tc.getExceptionType() != null ? xmlEsc(tc.getExceptionType()) : "AssertionError";
                    String trace = tc.getStackTrace()    != null ? xmlEsc(tc.getStackTrace())    : msg;
                    w.write(String.format(
                            "    <failure message=\"%s\" type=\"%s\">%s</failure>\n",
                            msg, type, trace
                    ));
                    if (tc.getAttempts() > 1) {
                        w.write(String.format(
                                "    <system-out>Attempts: %d</system-out>\n", tc.getAttempts()
                        ));
                    }
                    w.write("  </testcase>\n");
                }
            }

            w.write("</testsuite>\n");
        }

        log.info("JUnit XML report written to {}", file);
    }

    // -------------------------------------------------------------------------
    // OPEN_TEST_REPORTING_XML
    // -------------------------------------------------------------------------

    /**
     * Writes an Open Test Reporting XML report.
     *
     * <p>File name: {@code {suiteName}-open-test-report.xml}
     *
     * <p>Follows the
     * <a href="https://github.com/ota4j-team/open-test-reporting">Open Test Reporting</a>
     * schema, compatible with the JUnit Platform {@code OpenTestReportingListener}.
     * The schema uses an {@code e:events} root with {@code started}, {@code finished},
     * and nested {@code test} / {@code container} event elements.
     */
    private void writeOpenTestReportingXml(TestSuiteExecutionResult result) throws IOException {
        String safeName = safeName(result.getSuiteName());
        Path file = outputDir.resolve(safeName + "-open-test-report.xml");

        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            String suiteId  = "suite-1";
            String now      = ISO_INSTANT.format(Instant.now());

            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            w.write("<e:events xmlns:e=\"https://schemas.opentest4j.org/reporting/events/0.1.0\"\n");
            w.write("          xmlns:r=\"https://schemas.opentest4j.org/reporting/core/0.1.0\">\n");

            // Suite started
            w.write(String.format(
                    "  <e:started id=\"%s\" name=\"%s\" time=\"%s\">\n",
                    suiteId, xmlEsc(result.getSuiteName()), now
            ));
            if (result.getDescription() != null && !result.getDescription().isBlank()) {
                w.write(String.format(
                        "    <r:metadata><r:description>%s</r:description></r:metadata>\n",
                        xmlEsc(result.getDescription())
                ));
            }
            w.write("  </e:started>\n");

            // Each test case
            int idx = 1;
            for (TestCaseExecutionResult tc : result.getTestCaseResults()) {
                String testId = "test-" + idx++;
                w.write(String.format(
                        "  <e:started id=\"%s\" name=\"%s\" parentId=\"%s\" time=\"%s\"/>\n",
                        testId, xmlEsc(tc.getName()), suiteId, now
                ));

                if (tc.isPassed()) {
                    w.write(String.format(
                            "  <e:finished id=\"%s\" time=\"%s\" durationMs=\"%d\">\n",
                            testId, now, tc.getDurationMs()
                    ));
                    if (tc.getAttempts() > 1) {
                        w.write(String.format(
                                "    <r:metadata><r:entry key=\"attempts\" value=\"%d\"/></r:metadata>\n",
                                tc.getAttempts()
                        ));
                    }
                    w.write("    <r:result status=\"SUCCESSFUL\"/>\n");
                    w.write("  </e:finished>\n");
                } else {
                    w.write(String.format(
                            "  <e:finished id=\"%s\" time=\"%s\" durationMs=\"%d\">\n",
                            testId, now, tc.getDurationMs()
                    ));
                    if (tc.getAttempts() > 1) {
                        w.write(String.format(
                                "    <r:metadata><r:entry key=\"attempts\" value=\"%d\"/></r:metadata>\n",
                                tc.getAttempts()
                        ));
                    }
                    String type = tc.getExceptionType() != null ? tc.getExceptionType() : "AssertionError";
                    String msg  = tc.getErrorMessage()  != null ? xmlEsc(tc.getErrorMessage()) : "";
                    w.write(String.format(
                            "    <r:result status=\"FAILED\">\n"
                            + "      <r:failure type=\"%s\" message=\"%s\"/>\n"
                            + "    </r:result>\n",
                            xmlEsc(type), msg
                    ));
                    w.write("  </e:finished>\n");
                }
            }

            // Suite finished
            w.write(String.format(
                    "  <e:finished id=\"%s\" time=\"%s\">\n", suiteId, now
            ));
            w.write(String.format(
                    "    <r:result status=\"%s\"/>\n",
                    result.isAllPassed() ? "SUCCESSFUL" : "FAILED"
            ));
            w.write("  </e:finished>\n");
            w.write("</e:events>\n");
        }

        log.info("Open Test Reporting XML written to {}", file);
    }

    // -------------------------------------------------------------------------
    // JSON
    // -------------------------------------------------------------------------

    /**
     * Writes a simple JSON report.
     *
     * <p>File name: {@code {suiteName}-report.json}
     */
    private void writeJson(TestSuiteExecutionResult result) throws IOException {
        String safeName = safeName(result.getSuiteName());
        Path file = outputDir.resolve(safeName + "-report.json");

        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("{\n");
            w.write(String.format("  \"suiteName\": \"%s\",\n",  jsonEsc(result.getSuiteName())));
            w.write(String.format("  \"description\": \"%s\",\n",
                    result.getDescription() != null ? jsonEsc(result.getDescription()) : ""));
            w.write(String.format("  \"timestamp\": \"%s\",\n",  ISO_INSTANT.format(Instant.now())));
            w.write(String.format("  \"passed\": %b,\n",         result.isAllPassed()));
            w.write(String.format("  \"passedCount\": %d,\n",    result.getPassedCount()));
            w.write(String.format("  \"failedCount\": %d,\n",    result.getFailedCount()));
            w.write("  \"tests\": [\n");

            List<TestCaseExecutionResult> tests = result.getTestCaseResults();
            for (int i = 0; i < tests.size(); i++) {
                TestCaseExecutionResult tc = tests.get(i);
                boolean last = (i == tests.size() - 1);
                w.write("    {\n");
                w.write(String.format("      \"name\": \"%s\",\n",       jsonEsc(tc.getName())));
                w.write(String.format("      \"passed\": %b,\n",         tc.isPassed()));
                w.write(String.format("      \"durationMs\": %d,\n",     tc.getDurationMs()));
                w.write(String.format("      \"attempts\": %d,\n",       tc.getAttempts()));
                w.write(String.format("      \"errorMessage\": %s,\n",
                        tc.getErrorMessage() != null ? "\"" + jsonEsc(tc.getErrorMessage()) + "\"" : "null"));
                w.write(String.format("      \"exceptionType\": %s,\n",
                        tc.getExceptionType() != null ? "\"" + jsonEsc(tc.getExceptionType()) + "\"" : "null"));
                w.write(String.format("      \"stackTrace\": %s\n",
                        tc.getStackTrace() != null ? "\"" + jsonEsc(tc.getStackTrace()) + "\"" : "null"));
                w.write(last ? "    }\n" : "    },\n");
            }

            w.write("  ]\n");
            w.write("}\n");
        }

        log.info("JSON report written to {}", file);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String safeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String xmlEsc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String jsonEsc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Returns a new builder for {@link SuiteReportWriter}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link SuiteReportWriter}.
     *
     * <pre>{@code
     * SuiteReportWriter writer = SuiteReportWriter.builder()
     *         .outputDir(Path.of("build/test-reports"))
     *         .format(ReportFormat.JUNIT_XML)
     *         .format(ReportFormat.OPEN_TEST_REPORTING_XML)
     *         .format(ReportFormat.JSON)
     *         .build();
     * }</pre>
     */
    public static final class Builder {

        private Path outputDir = Path.of("build/test-reports");
        private final java.util.List<ReportFormat> formats = new java.util.ArrayList<>();

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
         * Adds a report format to produce.
         *
         * @param format the format to add
         * @return this builder
         */
        public Builder format(ReportFormat format) {
            this.formats.add(format);
            return this;
        }

        /**
         * Builds and returns the configured {@link SuiteReportWriter}.
         *
         * @return a new {@code SuiteReportWriter}
         * @throws IllegalStateException if no formats have been added
         */
        public SuiteReportWriter build() {
            if (formats.isEmpty()) {
                throw new IllegalStateException("At least one ReportFormat must be added via format(...)");
            }
            return new SuiteReportWriter(this);
        }
    }
}
