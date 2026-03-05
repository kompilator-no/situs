package no.testframework.javalibrary.domain;

/**
 * Immutable result of executing a single test case.
 *
 * <p>This is an <em>internal</em> domain object produced by
 * {@link no.testframework.javalibrary.runtime.TestRunner} after each test method runs
 * (or fails/times out). It is not part of the public API — the public counterpart is
 * {@link no.testframework.javalibrary.model.TestCaseResult}.
 *
 * <p>Contains everything needed to build a report: whether the test passed, how long
 * it took, and — on failure — the full exception chain (type, message, stack trace).
 */
public class TestCaseExecutionResult {

    private final String name;
    private final boolean passed;
    private final String errorMessage;
    private final String exceptionType;
    private final String stackTrace;
    private final long durationMs;

    /**
     * Convenience constructor for passing tests (no error information needed).
     *
     * @param name       the test case display name
     * @param passed     {@code true} if the test passed
     * @param errorMessage failure message, or {@code null} for passing tests
     * @param durationMs wall-clock execution time in milliseconds
     */
    public TestCaseExecutionResult(String name, boolean passed, String errorMessage, long durationMs) {
        this(name, passed, errorMessage, null, null, durationMs);
    }

    /**
     * Full constructor including exception details for failed tests.
     *
     * @param name          the test case display name
     * @param passed        {@code true} if the test passed
     * @param errorMessage  the root exception message, or {@code null} for passing tests
     * @param exceptionType fully-qualified class name of the root exception
     *                      (e.g. {@code "java.lang.AssertionError"}), or {@code null}
     * @param stackTrace    the full stack trace as a string, or {@code null} for timeouts/passes
     * @param durationMs    wall-clock execution time in milliseconds
     */
    public TestCaseExecutionResult(String name, boolean passed, String errorMessage,
                                   String exceptionType, String stackTrace, long durationMs) {
        this.name = name;
        this.passed = passed;
        this.errorMessage = errorMessage;
        this.exceptionType = exceptionType;
        this.stackTrace = stackTrace;
        this.durationMs = durationMs;
    }

    /** @return the display name of the test case */
    public String getName() { return name; }

    /** @return {@code true} if the test completed without throwing an exception */
    public boolean isPassed() { return passed; }

    /** @return the root cause exception message on failure, or {@code null} on success */
    public String getErrorMessage() { return errorMessage; }

    /**
     * @return the fully-qualified class name of the root exception (e.g.
     *         {@code "org.opentest4j.AssertionFailedError"}), or {@code null} if the test passed
     */
    public String getExceptionType() { return exceptionType; }

    /**
     * @return the full stack trace of the root exception as a multi-line string,
     *         or {@code null} for passing tests and timeout failures
     */
    public String getStackTrace() { return stackTrace; }

    /** @return wall-clock execution time from test start to completion/failure/timeout */
    public long getDurationMs() { return durationMs; }
}
