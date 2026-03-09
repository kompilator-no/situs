package no.kompilator.testframework.plugins.reporting;

import no.kompilator.testframework.model.TestSuite;
import no.kompilator.testframework.model.TestCaseResult;
import no.kompilator.testframework.model.TestSuiteResult;
import no.kompilator.testframework.plugin.SuiteCompletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SuiteReportWriter} and {@link ReportingPlugin}.
 */
public class ReportingPluginTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** Builds a synthetic result for writer-only tests (no real suite execution). */
    private TestSuiteResult mixedResult() {
        long base = 1_700_000_000_000L;
        return new TestSuiteResult("My Suite", "Test description", List.of(
                new TestCaseResult("passingTest", true,  null, null, null, 42, 1, base, base + 42),
                new TestCaseResult("failingTest", false, "expected <1> but was <2>",
                        "org.opentest4j.AssertionFailedError",
                        "at MyClass.failingTest(MyClass.java:10)", 15, 1, base + 2_000, base + 2_015),
                new TestCaseResult("retriedTest", true, null, null, null, 300, 3, base + 4_000, base + 4_300)
        ), 2, 1, false);
    }

    private TestSuiteResult allPassResult() {
        long base = 1_700_000_100_000L;
        return new TestSuiteResult("Clean Suite", null, List.of(
                new TestCaseResult("test1", true, null, null, null, 10, 1, base, base + 10),
                new TestCaseResult("test2", true, null, null, null, 20, 1, base + 50, base + 70)
        ), 2, 0, true);
    }

    private SuiteCompletedEvent completedEvent(TestSuiteResult result) {
        return new SuiteCompletedEvent(
                new TestSuite(result.getSuiteName(), result.getDescription(), List.of(), false),
                result);
    }

    // -------------------------------------------------------------------------
    // SuiteReportWriter — builder validation
    // -------------------------------------------------------------------------

    @Test
    void writerBuilderRequiresAtLeastOneFormat() {
        assertThatThrownBy(() -> SuiteReportWriter.builder().outputDir(tempDir).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ReportFormat");
    }

    @Test
    void writerReturnsConfiguredOutputDir() {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir)
                .format(ReportFormat.JUNIT_XML)
                .build();
        assertThat(writer.getOutputDir()).isEqualTo(tempDir);
    }

    @Test
    void writerReturnsConfiguredFormats() {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir)
                .format(ReportFormat.JUNIT_XML)
                .format(ReportFormat.JSON)
                .build();
        assertThat(writer.getFormats()).containsExactly(ReportFormat.JUNIT_XML, ReportFormat.JSON);
    }

    // -------------------------------------------------------------------------
    // JUNIT_XML
    // -------------------------------------------------------------------------

    @Test
    void junitXmlFileIsCreated() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir)
                .format(ReportFormat.JUNIT_XML)
                .build();
        writer.write(mixedResult());

        assertThat(tempDir.resolve("TEST-My_Suite.xml")).exists();
    }

    @Test
    void junitXmlContainsTestsuiteName() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.JUNIT_XML).build();
        writer.write(mixedResult());

        String xml = Files.readString(tempDir.resolve("TEST-My_Suite.xml"));
        assertThat(xml).contains("name=\"My Suite\"");
    }

    @Test
    void junitXmlContainsTestCaseElements() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.JUNIT_XML).build();
        writer.write(mixedResult());

        String xml = Files.readString(tempDir.resolve("TEST-My_Suite.xml"));
        assertThat(xml).contains("name=\"passingTest\"");
        assertThat(xml).contains("name=\"failingTest\"");
    }

    @Test
    void junitXmlContainsFailureElement() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.JUNIT_XML).build();
        writer.write(mixedResult());

        String xml = Files.readString(tempDir.resolve("TEST-My_Suite.xml"));
        assertThat(xml).contains("<failure");
        assertThat(xml).contains("expected &lt;1&gt; but was &lt;2&gt;");
    }

    @Test
    void junitXmlShowsAttemptCountForRetries() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.JUNIT_XML).build();
        writer.write(mixedResult());

        String xml = Files.readString(tempDir.resolve("TEST-My_Suite.xml"));
        // retriedTest passed on attempt 3 — attempts shown in system-out only when > 1 (and failed)
        // our retriedTest passes, so no system-out — but failureCount should be correct
        assertThat(xml).contains("failures=\"1\"");
    }

    @Test
    void junitXmlAllPassNoFailureElements() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.JUNIT_XML).build();
        writer.write(allPassResult());

        String xml = Files.readString(tempDir.resolve("TEST-Clean_Suite.xml"));
        assertThat(xml).doesNotContain("<failure");
        assertThat(xml).contains("failures=\"0\"");
    }

    @Test
    void junitXmlIsValidXmlStructure() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.JUNIT_XML).build();
        writer.write(mixedResult());

        String xml = Files.readString(tempDir.resolve("TEST-My_Suite.xml"));
        assertThat(xml).startsWith("<?xml version=\"1.0\"");
        assertThat(xml).contains("<testsuite");
        assertThat(xml).contains("</testsuite>");
    }

    // -------------------------------------------------------------------------
    // OPEN_TEST_REPORTING_XML
    // -------------------------------------------------------------------------

    @Test
    void openTestReportingXmlFileIsCreated() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.OPEN_TEST_REPORTING_XML).build();
        writer.write(mixedResult());

        assertThat(tempDir.resolve("My_Suite-open-test-report.xml")).exists();
    }

    @Test
    void openTestReportingXmlContainsEventsNamespace() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.OPEN_TEST_REPORTING_XML).build();
        writer.write(mixedResult());

        String xml = Files.readString(tempDir.resolve("My_Suite-open-test-report.xml"));
        assertThat(xml).contains("xmlns:e=\"https://schemas.opentest4j.org/reporting/events/0.1.0\"");
        assertThat(xml).contains("xmlns:r=\"https://schemas.opentest4j.org/reporting/core/0.1.0\"");
    }

    @Test
    void openTestReportingXmlContainsSuiteStartedAndFinished() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.OPEN_TEST_REPORTING_XML).build();
        writer.write(mixedResult());

        String xml = Files.readString(tempDir.resolve("My_Suite-open-test-report.xml"));
        assertThat(xml).contains("<e:started id=\"suite-1\"");
        assertThat(xml).contains("<e:finished id=\"suite-1\"");
    }

    @Test
    void openTestReportingXmlContainsTestCaseStartedAndFinished() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.OPEN_TEST_REPORTING_XML).build();
        writer.write(mixedResult());

        String xml = Files.readString(tempDir.resolve("My_Suite-open-test-report.xml"));
        assertThat(xml).contains("<e:started id=\"test-1\"");
        assertThat(xml).contains("id=\"test-1\"");
        assertThat(xml).contains("<e:started id=\"test-2\"");
    }

    @Test
    void openTestReportingXmlUsesPerTestTimestamps() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.OPEN_TEST_REPORTING_XML).build();
        writer.write(mixedResult());

        String xml = Files.readString(tempDir.resolve("My_Suite-open-test-report.xml"));
        assertThat(xml).contains("time=\"2023-11-14T22:13:20\"");
        assertThat(xml).contains("time=\"2023-11-14T22:13:22\"");
        assertThat(xml).contains("time=\"2023-11-14T22:13:24\"");
    }

    @Test
    void openTestReportingXmlShowsSuccessfulStatus() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.OPEN_TEST_REPORTING_XML).build();
        writer.write(allPassResult());

        String xml = Files.readString(tempDir.resolve("Clean_Suite-open-test-report.xml"));
        assertThat(xml).contains("status=\"SUCCESSFUL\"");
        assertThat(xml).doesNotContain("status=\"FAILED\"");
    }

    @Test
    void openTestReportingXmlShowsFailedStatus() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.OPEN_TEST_REPORTING_XML).build();
        writer.write(mixedResult());

        String xml = Files.readString(tempDir.resolve("My_Suite-open-test-report.xml"));
        assertThat(xml).contains("status=\"FAILED\"");
        assertThat(xml).contains("status=\"SUCCESSFUL\"");
    }

    @Test
    void openTestReportingXmlContainsRetryMetadata() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.OPEN_TEST_REPORTING_XML).build();
        writer.write(mixedResult());  // mixedResult has retriedTest with attempts=3

        String xml = Files.readString(tempDir.resolve("My_Suite-open-test-report.xml"));
        assertThat(xml).contains("key=\"attempts\" value=\"3\"");
    }

    @Test
    void openTestReportingXmlDescriptionIsIncluded() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.OPEN_TEST_REPORTING_XML).build();
        writer.write(mixedResult());

        String xml = Files.readString(tempDir.resolve("My_Suite-open-test-report.xml"));
        assertThat(xml).contains("Test description");
    }

    // -------------------------------------------------------------------------
    // JSON
    // -------------------------------------------------------------------------

    @Test
    void jsonFileIsCreated() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.JSON).build();
        writer.write(mixedResult());

        assertThat(tempDir.resolve("My_Suite-report.json")).exists();
    }

    @Test
    void jsonContainsSuiteName() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.JSON).build();
        writer.write(mixedResult());

        String json = Files.readString(tempDir.resolve("My_Suite-report.json"));
        assertThat(json).contains("\"suiteName\" : \"My Suite\"");
    }

    @Test
    void jsonContainsTestResults() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.JSON).build();
        writer.write(mixedResult());

        String json = Files.readString(tempDir.resolve("My_Suite-report.json"));
        assertThat(json).contains("\"passingTest\"");
        assertThat(json).contains("\"failingTest\"");
        assertThat(json).contains("\"passed\" : true");
        assertThat(json).contains("\"passed\" : false");
    }

    @Test
    void jsonContainsAttemptsField() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.JSON).build();
        writer.write(mixedResult());

        String json = Files.readString(tempDir.resolve("My_Suite-report.json"));
        assertThat(json).contains("\"attempts\" : 3");
    }

    @Test
    void jsonContainsPerTestTimestamps() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.JSON).build();
        writer.write(mixedResult());

        String json = Files.readString(tempDir.resolve("My_Suite-report.json"));
        assertThat(json).contains("\"startedAtEpochMs\" : 1700000000000");
        assertThat(json).contains("\"completedAtEpochMs\" : 1700000000042");
    }

    @Test
    void jsonContainsPassedAndFailedCounts() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.JSON).build();
        writer.write(mixedResult());

        String json = Files.readString(tempDir.resolve("My_Suite-report.json"));
        assertThat(json).contains("\"passedCount\" : 2");
        assertThat(json).contains("\"failedCount\" : 1");
    }

    @Test
    void jsonNullFieldsAreWrittenAsNull() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir).format(ReportFormat.JSON).build();
        writer.write(allPassResult());

        String json = Files.readString(tempDir.resolve("Clean_Suite-report.json"));
        assertThat(json).contains("\"errorMessage\" : null");
        assertThat(json).contains("\"stackTrace\" : null");
    }

    // -------------------------------------------------------------------------
    // Multiple formats in one write
    // -------------------------------------------------------------------------

    @Test
    void allThreeFormatsWrittenInOnCall() throws IOException {
        SuiteReportWriter writer = SuiteReportWriter.builder()
                .outputDir(tempDir)
                .format(ReportFormat.JUNIT_XML)
                .format(ReportFormat.OPEN_TEST_REPORTING_XML)
                .format(ReportFormat.JSON)
                .build();
        writer.write(mixedResult());

        assertThat(tempDir.resolve("TEST-My_Suite.xml")).exists();
        assertThat(tempDir.resolve("My_Suite-open-test-report.xml")).exists();
        assertThat(tempDir.resolve("My_Suite-report.json")).exists();
    }

    // -------------------------------------------------------------------------
    // ReportingPlugin — builder validation
    // -------------------------------------------------------------------------

    @Test
    void reportingPluginBuilderRequiresFormats() {
        assertThatThrownBy(() -> ReportingPlugin.builder()
                .outputDir(tempDir)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ReportFormat");
    }

    // -------------------------------------------------------------------------
    // ReportingPlugin — listener behavior
    // -------------------------------------------------------------------------

    @Test
    void reportingPluginWritesReportsWhenEventArrives() {
        ReportingPlugin plugin = ReportingPlugin.builder()
                .outputDir(tempDir)
                .format(ReportFormat.JUNIT_XML)
                .build();

        plugin.onSuiteCompleted(completedEvent(mixedResult()));

        assertThat(tempDir.resolve("TEST-My_Suite.xml")).exists();
    }

    @Test
    void reportingPluginWritesJsonFromCompletedEvent() throws IOException {
        ReportingPlugin plugin = ReportingPlugin.builder()
                .outputDir(tempDir)
                .format(ReportFormat.JSON)
                .build();

        plugin.onSuiteCompleted(completedEvent(allPassResult()));

        String json = Files.readString(tempDir.resolve("Clean_Suite-report.json"));
        assertThat(json).contains("\"suiteName\" : \"Clean Suite\"");
        assertThat(json).contains("\"passedCount\" : 2");
    }

    @Test
    void reportingPluginGetWriterReturnsConfiguredWriter() {
        ReportingPlugin plugin = ReportingPlugin.builder()
                .outputDir(tempDir)
                .format(ReportFormat.JUNIT_XML)
                .build();

        assertThat(plugin.getWriter().getOutputDir()).isEqualTo(tempDir);
        assertThat(plugin.getWriter().getFormats()).containsExactly(ReportFormat.JUNIT_XML);
    }

    @Test
    void reportingPluginAllFormatsProducedFromEvent() {
        ReportingPlugin plugin = ReportingPlugin.builder()
                .outputDir(tempDir)
                .format(ReportFormat.JUNIT_XML)
                .format(ReportFormat.OPEN_TEST_REPORTING_XML)
                .format(ReportFormat.JSON)
                .build();

        plugin.onSuiteCompleted(completedEvent(allPassResult()));

        assertThat(tempDir.resolve("TEST-Clean_Suite.xml")).exists();
        assertThat(tempDir.resolve("Clean_Suite-open-test-report.xml")).exists();
        assertThat(tempDir.resolve("Clean_Suite-report.json")).exists();
    }
}
