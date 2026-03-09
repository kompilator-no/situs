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
    private final int attempts;
    private final long startedAtEpochMs;
    private final long completedAtEpochMs;

    /**
     * Convenience constructor for passing tests with a single attempt.
     *
     * @param name         the test case display name
     * @param passed       {@code true} if the test passed
     * @param errorMessage failure message, or {@code null} for passing tests
     * @param durationMs   wall-clock execution time in milliseconds
     */
    public TestCaseExecutionResult(String name, boolean passed, String errorMessage, long durationMs) {
        this(name, passed, errorMessage, null, null, durationMs, 1, 0, 0);
    }

    /**
     * Full constructor including exception details for failed tests, with single attempt.
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
        this(name, passed, errorMessage, exceptionType, stackTrace, durationMs, 1, 0, 0);
    }

    /**
     * Full constructor including attempt count — used when retries are configured.
     *
     * @param name          the test case display name
     * @param passed        {@code true} if the test passed
     * @param errorMessage  the root exception message, or {@code null} for passing tests
     * @param exceptionType fully-qualified class name of the root exception, or {@code null}
     * @param stackTrace    the full stack trace as a string, or {@code null} for timeouts/passes
     * @param durationMs    total wall-clock time across all attempts in milliseconds
     * @param attempts      total number of attempts made (1 = no retries occurred)
     */
    public TestCaseExecutionResult(String name, boolean passed, String errorMessage,
                                   String exceptionType, String stackTrace,
                                   long durationMs, int attempts) {
        this(name, passed, errorMessage, exceptionType, stackTrace, durationMs, attempts, 0, 0);
    }

    /**
     * Full constructor including execution timestamps.
     *
     * @param name               the test case display name
     * @param passed             {@code true} if the test passed
     * @param errorMessage       the root exception message, or {@code null} for passing tests
     * @param exceptionType      fully-qualified class name of the root exception, or {@code null}
     * @param stackTrace         the full stack trace as a string, or {@code null} for timeouts/passes
     * @param durationMs         total wall-clock time across all attempts in milliseconds
     * @param attempts           total number of attempts made (1 = no retries occurred)
     * @param startedAtEpochMs   wall-clock start time of the first attempt in epoch milliseconds
     * @param completedAtEpochMs wall-clock completion time of the final attempt in epoch milliseconds
     */
    public TestCaseExecutionResult(String name, boolean passed, String errorMessage,
                                   String exceptionType, String stackTrace,
                                   long durationMs, int attempts,
                                   long startedAtEpochMs, long completedAtEpochMs) {
        this.name = name;
        this.passed = passed;
        this.errorMessage = errorMessage;
        this.exceptionType = exceptionType;
        this.stackTrace = stackTrace;
        this.durationMs = durationMs;
        this.attempts = attempts;
        this.startedAtEpochMs = startedAtEpochMs;
        this.completedAtEpochMs = completedAtEpochMs;
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

    /** @return total wall-clock execution time across all attempts in milliseconds */
    public long getDurationMs() { return durationMs; }

    /**
     * @return total number of execution attempts made; {@code 1} means the test
     *         passed or failed on the first try with no retries
     */
    public int getAttempts() { return attempts; }

    /** @return wall-clock start time of the first attempt in epoch milliseconds, or {@code 0} if unavailable */
    public long getStartedAtEpochMs() { return startedAtEpochMs; }

    /** @return wall-clock completion time of the final attempt in epoch milliseconds, or {@code 0} if unavailable */
    public long getCompletedAtEpochMs() { return completedAtEpochMs; }
}
