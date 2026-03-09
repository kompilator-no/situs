package no.kompilator.testframework.plugins.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.kompilator.testframework.model.TestCaseResult;
import no.kompilator.testframework.model.TestSuiteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes a {@link TestSuiteResult} to disk in one or more structured formats.
 *
 * <p>Three formats are supported — see {@link ReportFormat} for details:
 * <ul>
 *   <li>{@link ReportFormat#JUNIT_XML} — Surefire/JUnit XML (widest CI tool support),
 *       written via the JDK {@link DocumentBuilder} DOM API.</li>
 *   <li>{@link ReportFormat#OPEN_TEST_REPORTING_XML} — Open Test Reporting XML,
 *       also written via the JDK DOM API.</li>
 *   <li>{@link ReportFormat#JSON} — pretty-printed JSON via
 *       <a href="https://github.com/FasterXML/jackson-databind">Jackson Databind</a>.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * SuiteReportWriter writer = SuiteReportWriter.builder()
 *         .outputDir(Path.of("build/test-reports"))
 *         .format(ReportFormat.JUNIT_XML)
 *         .format(ReportFormat.OPEN_TEST_REPORTING_XML)
 *         .format(ReportFormat.JSON)
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

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

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
    public void write(TestSuiteResult result) throws IOException {
        Files.createDirectories(outputDir);
        for (ReportFormat format : formats) {
            switch (format) {
                case JUNIT_XML               -> writeJUnitXml(result);
                case OPEN_TEST_REPORTING_XML -> writeOpenTestReportingXml(result);
                case JSON                    -> writeJson(result);
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
    // JUNIT_XML  — JDK DOM API
    // -------------------------------------------------------------------------

    /**
     * Writes a Surefire/JUnit XML report using the JDK {@link DocumentBuilder} DOM API.
     *
     * <p>File name: {@code TEST-{suiteName}.xml}
     */
    private void writeJUnitXml(TestSuiteResult result) throws IOException {
        try {
            Document doc = newDocument();
            String suiteTimestamp = ISO_INSTANT.format(resolveSuiteStartedAt(result));

            long totalMs = result.getResults().stream()
                    .mapToLong(TestCaseResult::getDurationMs).sum();

            Element suite = doc.createElement("testsuite");
            suite.setAttribute("name",      result.getSuiteName());
            suite.setAttribute("tests",     String.valueOf(result.getResults().size()));
            suite.setAttribute("failures",  String.valueOf(result.getFailedCount()));
            suite.setAttribute("errors",    "0");
            suite.setAttribute("skipped",   "0");
            suite.setAttribute("time",      String.format("%.3f", totalMs / 1000.0));
            suite.setAttribute("timestamp", suiteTimestamp);
            doc.appendChild(suite);

            for (TestCaseResult tc : result.getResults()) {
                Element testcase = doc.createElement("testcase");
                testcase.setAttribute("name",      tc.getName());
                testcase.setAttribute("classname", result.getSuiteName());
                testcase.setAttribute("time",      String.format("%.3f", tc.getDurationMs() / 1000.0));

                if (!tc.isPassed()) {
                    Element failure = doc.createElement("failure");
                    failure.setAttribute("message", nullToEmpty(tc.getErrorMessage()));
                    failure.setAttribute("type",
                            tc.getExceptionType() != null ? tc.getExceptionType() : "AssertionError");
                    failure.setTextContent(
                            tc.getStackTrace() != null ? tc.getStackTrace() : nullToEmpty(tc.getErrorMessage()));
                    testcase.appendChild(failure);

                    if (tc.getAttempts() > 1) {
                        Element sysOut = doc.createElement("system-out");
                        sysOut.setTextContent("Attempts: " + tc.getAttempts());
                        testcase.appendChild(sysOut);
                    }
                }
                suite.appendChild(testcase);
            }

            Path file = outputDir.resolve("TEST-" + safeName(result.getSuiteName()) + ".xml");
            writeXml(doc, file);
            log.info("JUnit XML report written to {}", file);

        } catch (ParserConfigurationException | TransformerException e) {
            throw new IOException("Failed to write JUnit XML report for: " + result.getSuiteName(), e);
        }
    }

    // -------------------------------------------------------------------------
    // OPEN_TEST_REPORTING_XML  — JDK DOM API
    // -------------------------------------------------------------------------

    /**
     * Writes an Open Test Reporting XML report using the JDK {@link DocumentBuilder} DOM API.
     *
     * <p>File name: {@code {suiteName}-open-test-report.xml}
     */
    private void writeOpenTestReportingXml(TestSuiteResult result) throws IOException {
        try {
            // Use a plain (non-namespace-aware) document so the serializer preserves
            // our e:/r: prefixes exactly as written rather than rewriting them.
            Document doc   = newDocument();
            String suiteId = "suite-1";
            Instant suiteStartedAt = resolveSuiteStartedAt(result);
            Instant suiteFinishedAt = resolveSuiteCompletedAt(result);

            Element events = doc.createElement("e:events");
            events.setAttribute("xmlns:e", "https://schemas.opentest4j.org/reporting/events/0.1.0");
            events.setAttribute("xmlns:r", "https://schemas.opentest4j.org/reporting/core/0.1.0");
            doc.appendChild(events);

            // Suite started
            Element suiteStarted = doc.createElement("e:started");
            suiteStarted.setAttribute("id",   suiteId);
            suiteStarted.setAttribute("name", result.getSuiteName());
            suiteStarted.setAttribute("time", ISO_INSTANT.format(suiteStartedAt));
            if (result.getDescription() != null && !result.getDescription().isBlank()) {
                Element meta = doc.createElement("r:metadata");
                Element desc = doc.createElement("r:description");
                desc.setTextContent(result.getDescription());
                meta.appendChild(desc);
                suiteStarted.appendChild(meta);
            }
            events.appendChild(suiteStarted);

            // Each test case
            int idx = 1;
            for (TestCaseResult tc : result.getResults()) {
                String testId = "test-" + idx++;

                Element started = doc.createElement("e:started");
                started.setAttribute("id",       testId);
                started.setAttribute("name",     tc.getName());
                started.setAttribute("parentId", suiteId);
                started.setAttribute("time",     ISO_INSTANT.format(resolveTestStartedAt(tc)));
                events.appendChild(started);

                Element finished = doc.createElement("e:finished");
                finished.setAttribute("id",         testId);
                finished.setAttribute("time",       ISO_INSTANT.format(resolveTestCompletedAt(tc)));
                finished.setAttribute("durationMs", String.valueOf(tc.getDurationMs()));

                if (tc.getAttempts() > 1) {
                    Element meta  = doc.createElement("r:metadata");
                    Element entry = doc.createElement("r:entry");
                    entry.setAttribute("key",   "attempts");
                    entry.setAttribute("value", String.valueOf(tc.getAttempts()));
                    meta.appendChild(entry);
                    finished.appendChild(meta);
                }

                Element resultEl = doc.createElement("r:result");
                if (tc.isPassed()) {
                    resultEl.setAttribute("status", "SUCCESSFUL");
                } else {
                    resultEl.setAttribute("status", "FAILED");
                    Element failure = doc.createElement("r:failure");
                    failure.setAttribute("type",
                            tc.getExceptionType() != null ? tc.getExceptionType() : "AssertionError");
                    failure.setAttribute("message", nullToEmpty(tc.getErrorMessage()));
                    resultEl.appendChild(failure);
                }
                finished.appendChild(resultEl);
                events.appendChild(finished);
            }

            // Suite finished
            Element suiteFinished = doc.createElement("e:finished");
            suiteFinished.setAttribute("id",   suiteId);
            suiteFinished.setAttribute("time", ISO_INSTANT.format(suiteFinishedAt));
            Element suiteResult = doc.createElement("r:result");
            suiteResult.setAttribute("status", result.isAllPassed() ? "SUCCESSFUL" : "FAILED");
            suiteFinished.appendChild(suiteResult);
            events.appendChild(suiteFinished);

            Path file = outputDir.resolve(safeName(result.getSuiteName()) + "-open-test-report.xml");
            writeXml(doc, file);
            log.info("Open Test Reporting XML written to {}", file);

        } catch (ParserConfigurationException | TransformerException e) {
            throw new IOException("Failed to write OTR XML report for: " + result.getSuiteName(), e);
        }
    }

    // -------------------------------------------------------------------------
    // JSON  — Jackson
    // -------------------------------------------------------------------------

    /**
     * Writes a pretty-printed JSON report using Jackson.
     *
     * <p>File name: {@code {suiteName}-report.json}
     */
    private void writeJson(TestSuiteResult result) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("suiteName",   result.getSuiteName());
        root.put("description", nullToEmpty(result.getDescription()));
        root.put("timestamp",   ISO_INSTANT.format(Instant.now()));
        root.put("passed",      result.isAllPassed());
        root.put("passedCount", result.getPassedCount());
        root.put("failedCount", result.getFailedCount());

        ArrayNode tests = root.putArray("tests");
        for (TestCaseResult tc : result.getResults()) {
            ObjectNode node = tests.addObject();
            node.put("name",          tc.getName());
            node.put("passed",        tc.isPassed());
            node.put("durationMs",    tc.getDurationMs());
            node.put("attempts",      tc.getAttempts());
            node.put("startedAtEpochMs", tc.getStartedAtEpochMs());
            node.put("completedAtEpochMs", tc.getCompletedAtEpochMs());
            if (tc.getErrorMessage()  != null) node.put("errorMessage",  tc.getErrorMessage());
            else                               node.putNull("errorMessage");
            if (tc.getExceptionType() != null) node.put("exceptionType", tc.getExceptionType());
            else                               node.putNull("exceptionType");
            if (tc.getStackTrace()    != null) node.put("stackTrace",    tc.getStackTrace());
            else                               node.putNull("stackTrace");
        }

        Path file = outputDir.resolve(safeName(result.getSuiteName()) + "-report.json");
        MAPPER.writeValue(file.toFile(), root);
        log.info("JSON report written to {}", file);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Document newDocument() throws ParserConfigurationException {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    private static Instant resolveSuiteStartedAt(TestSuiteResult result) {
        return result.getResults().stream()
                .mapToLong(TestCaseResult::getStartedAtEpochMs)
                .filter(value -> value > 0)
                .min()
                .stream()
                .mapToObj(Instant::ofEpochMilli)
                .findFirst()
                .orElseGet(Instant::now);
    }

    private static Instant resolveSuiteCompletedAt(TestSuiteResult result) {
        return result.getResults().stream()
                .mapToLong(TestCaseResult::getCompletedAtEpochMs)
                .filter(value -> value > 0)
                .max()
                .stream()
                .mapToObj(Instant::ofEpochMilli)
                .findFirst()
                .orElseGet(Instant::now);
    }

    private static Instant resolveTestStartedAt(TestCaseResult result) {
        return result.getStartedAtEpochMs() > 0
                ? Instant.ofEpochMilli(result.getStartedAtEpochMs())
                : Instant.now();
    }

    private static Instant resolveTestCompletedAt(TestCaseResult result) {
        return result.getCompletedAtEpochMs() > 0
                ? Instant.ofEpochMilli(result.getCompletedAtEpochMs())
                : Instant.now();
    }


    private static void writeXml(Document doc, Path file)
            throws TransformerException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT,               "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING,             "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(new DOMSource(doc), new StreamResult(file.toFile()));
    }

    private static String safeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
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
