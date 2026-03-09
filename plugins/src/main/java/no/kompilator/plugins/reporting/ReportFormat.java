package no.kompilator.plugins.reporting;

/**
 * Output formats supported by {@link SuiteReportWriter}.
 *
 * <ul>
 *   <li>{@link #JUNIT_XML} — Surefire / JUnit XML (understood by every CI server,
 *       IntelliJ, Eclipse, GitHub Actions test summaries, etc.)</li>
 *   <li>{@link #OPEN_TEST_REPORTING_XML} — the new
 *       <a href="https://github.com/ota4j-team/open-test-reporting">Open Test Reporting</a>
 *       XML schema, compatible with the JUnit Platform OTR listener format.</li>
 *   <li>{@link #JSON} — a simple JSON array of test results for custom dashboards
 *       or further processing.</li>
 * </ul>
 */
public enum ReportFormat {

    /**
     * Apache Maven Surefire / JUnit XML format.
     *
     * <p>Produces a file named {@code TEST-{suiteName}.xml}.
     * Understood by Jenkins, GitHub Actions, GitLab CI, IntelliJ IDEA, and most
     * other CI / CD tools without any extra configuration.
     */
    JUNIT_XML,

    /**
     * Open Test Reporting XML format.
     *
     * <p>Produces a file named {@code {suiteName}-open-test-report.xml}.
     * Follows the schema defined by
     * <a href="https://github.com/ota4j-team/open-test-reporting">ota4j-team/open-test-reporting</a>
     * and is compatible with the JUnit Platform {@code OpenTestReportingListener}.
     */
    OPEN_TEST_REPORTING_XML,

    /**
     * Simple JSON format.
     *
     * <p>Produces a file named {@code {suiteName}-report.json}.
     * Useful for custom dashboards, post-processing scripts, or archiving results.
     */
    JSON
}
