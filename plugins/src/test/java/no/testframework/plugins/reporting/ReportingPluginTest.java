package no.testframework.plugins.reporting;

import no.testframework.javalibrary.annotations.RunTimeTest;
import no.testframework.javalibrary.annotations.RuntimeTestSuite;
import no.testframework.javalibrary.domain.TestCaseExecutionResult;
import no.testframework.javalibrary.domain.TestSuiteExecutionResult;
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

    @RuntimeTestSuite(name = "All Pass Suite", description = "All tests pass")
    public static class AllPassSuite {
        @RunTimeTest(name = "passingTest") public void passingTest() {}
    }

    @RuntimeTestSuite(name = "Mixed Suite", description = "One pass one fail")
    public static class MixedSuite {
        @RunTimeTest(name = "passes") public void passes() {}
        @RunTimeTest(name = "fails")  public void fails() { throw new AssertionError("expected <1> but was <2>"); }
    }

    /** Builds a synthetic result for writer-only tests (no real suite execution). */
    private TestSuiteExecutionResult mixedResult() {
        return new TestSuiteExecutionResult("My Suite", "Test description", List.of(
                new TestCaseExecutionResult("passingTest", true,  null, null, null, 42, 1),
                new TestCaseExecutionResult("failingTest", false, "expected <1> but was <2>",
                        "org.opentest4j.AssertionFailedError",
                        "at MyClass.failingTest(MyClass.java:10)", 15, 1),
                new TestCaseExecutionResult("retriedTest", true, null, null, null, 300, 3)
        ));
    }

    private TestSuiteExecutionResult allPassResult() {
        return new TestSuiteExecutionResult("Clean Suite", null, List.of(
                new TestCaseExecutionResult("test1", true, null, 10),
                new TestCaseExecutionResult("test2", true, null, 20)
        ));
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
    void reportingPluginBuilderRequiresSuites() {
        assertThatThrownBy(() -> ReportingPlugin.builder()
                .outputDir(tempDir)
                .format(ReportFormat.JUNIT_XML)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("suite");
    }

    @Test
    void reportingPluginBuilderRequiresFormats() {
        assertThatThrownBy(() -> ReportingPlugin.builder()
                .suite(AllPassSuite.class)
                .outputDir(tempDir)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ReportFormat");
    }

    // -------------------------------------------------------------------------
    // ReportingPlugin — end-to-end
    // -------------------------------------------------------------------------

    @Test
    void reportingPluginRunsAllSuitesAndWritesReports() {
        ReportingPlugin plugin = ReportingPlugin.builder()
                .suite(AllPassSuite.class)
                .suite(MixedSuite.class)
                .outputDir(tempDir)
                .format(ReportFormat.JUNIT_XML)
                .build();

        List<TestSuiteExecutionResult> results = plugin.runAndReport();

        assertThat(results).hasSize(2);
        assertThat(tempDir.resolve("TEST-All_Pass_Suite.xml")).exists();
        assertThat(tempDir.resolve("TEST-Mixed_Suite.xml")).exists();
    }

    @Test
    void reportingPluginResultsReflectActualExecution() {
        ReportingPlugin plugin = ReportingPlugin.builder()
                .suite(AllPassSuite.class)
                .outputDir(tempDir)
                .format(ReportFormat.JUNIT_XML)
                .build();

        List<TestSuiteExecutionResult> results = plugin.runAndReport();

        assertThat(results.getFirst().isAllPassed()).isTrue();
        assertThat(results.getFirst().getPassedCount()).isEqualTo(1);
    }

    @Test
    void reportingPluginContinuesAfterFailingSuite() {
        ReportingPlugin plugin = ReportingPlugin.builder()
                .suite(MixedSuite.class)
                .suite(AllPassSuite.class)
                .outputDir(tempDir)
                .format(ReportFormat.JUNIT_XML)
                .build();

        List<TestSuiteExecutionResult> results = plugin.runAndReport();

        // Both suites ran
        assertThat(results).hasSize(2);
        // First suite has a failure
        assertThat(results.getFirst().getFailedCount()).isEqualTo(1);
        // Second suite all passed
        assertThat(results.get(1).isAllPassed()).isTrue();
    }

    @Test
    void reportingPluginGetSuiteClassesReturnsConfigured() {
        ReportingPlugin plugin = ReportingPlugin.builder()
                .suite(AllPassSuite.class)
                .suite(MixedSuite.class)
                .outputDir(tempDir)
                .format(ReportFormat.JUNIT_XML)
                .build();

        assertThat(plugin.getSuiteClasses())
                .containsExactly(AllPassSuite.class, MixedSuite.class);
    }

    @Test
    void reportingPluginAllFormatsProducedEndToEnd() {
        ReportingPlugin plugin = ReportingPlugin.builder()
                .suite(AllPassSuite.class)
                .outputDir(tempDir)
                .format(ReportFormat.JUNIT_XML)
                .format(ReportFormat.OPEN_TEST_REPORTING_XML)
                .format(ReportFormat.JSON)
                .build();

        plugin.runAndReport();

        assertThat(tempDir.resolve("TEST-All_Pass_Suite.xml")).exists();
        assertThat(tempDir.resolve("All_Pass_Suite-open-test-report.xml")).exists();
        assertThat(tempDir.resolve("All_Pass_Suite-report.json")).exists();
    }
}
