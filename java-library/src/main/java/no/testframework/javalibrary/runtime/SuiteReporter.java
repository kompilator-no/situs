package no.testframework.javalibrary.runtime;

import no.testframework.javalibrary.domain.TestCaseExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Formats and writes structured, human-readable suite reports to the SLF4J logger.
 *
 * <p>Called automatically by {@link RuntimeTestSuiteRunner} and
 * {@link no.testframework.javalibrary.spring.TestFrameworkService} after a suite
 * finishes. Can also be called directly for standalone (non-Spring) usage.
 *
 * <p>Example output:
 * <pre>{@code
 * ╔══════════════════════════════════════════════════════════╗
 * ║  TEST SUITE: My Suite                                    ║
 * ║  Description: Checks the payment flow                   ║
 * ╠══════════════════════════════════════════════════════════╣
 * ║  ✔  passingTest                               (   12 ms) ║
 * ║  ✘  failingTest                               (    4 ms) ║
 * ║     -> expected <foo> but was <bar>                      ║
 * ║  ⏱  slowTest                                  (  101 ms) ║
 * ║     -> Test timed out after 100ms                        ║
 * ╠══════════════════════════════════════════════════════════╣
 * ║  PASSED: 1   FAILED: 2   TOTAL: 3   TIME: 117 ms         ║
 * ╚══════════════════════════════════════════════════════════╝
 * }</pre>
 *
 * @see RuntimeTestSuiteRunner
 * @see no.testframework.javalibrary.domain.TestCaseExecutionResult
 */
public class SuiteReporter {

    private static final Logger log = LoggerFactory.getLogger(SuiteReporter.class);

    private static final int WIDTH      = 62;   // inner content width (between ║ and ║)
    private static final int NAME_COL   = 42;   // characters reserved for test name + icon
    private static final String TOP     = "╔" + "═".repeat(WIDTH) + "╗";
    private static final String DIVIDER = "╠" + "═".repeat(WIDTH) + "╣";
    private static final String BOTTOM  = "╚" + "═".repeat(WIDTH) + "╝";

    private SuiteReporter() {}

    /**
     * Formats and logs a structured report for the given suite execution results.
     *
     * @param suiteName   the display name of the suite
     * @param description optional description shown under the suite name (may be null or blank)
     * @param results     the individual test case results to include in the report
     */
    public static void report(String suiteName, String description,
                              List<TestCaseExecutionResult> results) {
        long totalMs = results.stream().mapToLong(TestCaseExecutionResult::getDurationMs).sum();
        long passed  = results.stream().filter(TestCaseExecutionResult::isPassed).count();
        long failed  = results.size() - passed;

        log.info(TOP);
        log.info(row("TEST SUITE: " + suiteName));
        if (description != null && !description.isBlank()) {
            log.info(row("Description: " + description));
        }
        log.info(DIVIDER);

        for (TestCaseExecutionResult r : results) {
            String icon   = icon(r);
            String name   = truncate(r.getName(), NAME_COL - 4); // 4 = icon + spaces
            String dur    = String.format("(%5d ms)", r.getDurationMs());
            // name column left-padded, duration right-aligned
            String line   = icon + "  " + padRight(name, NAME_COL - 4) + " " + dur;
            log.info(row(line));
            if (!r.isPassed() && r.getErrorMessage() != null) {
                log.info(row("   → " + truncate(r.getErrorMessage(), WIDTH - 6)));
            }
        }

        log.info(DIVIDER);
        log.info(row(String.format("PASSED: %-4d FAILED: %-4d TOTAL: %-4d TIME: %d ms",
                passed, failed, results.size(), totalMs)));
        log.info(BOTTOM);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String icon(TestCaseExecutionResult r) {
        if (!r.isPassed()) {
            return r.getErrorMessage() != null && r.getErrorMessage().contains("timed out") ? "⏱" : "✘";
        }
        return "✔";
    }

    /** Wraps content in a box row: ║  content  ║ */
    private static String row(String content) {
        return "║  " + padRight(truncate(content, WIDTH - 2), WIDTH - 2) + "║";
    }

    private static String padRight(String s, int width) {
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
