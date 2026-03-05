package no.testframework.javalibrary.model;

/**
 * Immutable result of executing a single test case.
 *
 * <p>Part of the shared model layer ({@code no.testframework.javalibrary.model}) used by
 * both the pure-Java runtime ({@link no.testframework.javalibrary.runtime.TestRunner})
 * and the Spring API layer ({@link no.testframework.javalibrary.spring.model.SuiteRunStatus}).
 *
 * <p>On failure the {@code exceptionType}, {@code errorMessage}, and {@code stackTrace}
 * fields are populated so callers can diagnose the problem without needing server log access.
 */
public class TestCaseResult {

    private String name;
    private boolean passed;
    private String errorMessage;
    private String exceptionType;
    private String stackTrace;
    private long durationMs;
    private int attempts;

    /** No-arg constructor required by Jackson for deserialisation. */
    public TestCaseResult() {}

    /**
     * Convenience constructor for passing tests with a single attempt.
     *
     * @param name         display name of the test
     * @param passed       {@code true} if the test passed
     * @param errorMessage failure message, or {@code null}
     * @param durationMs   wall-clock execution time in milliseconds
     */
    public TestCaseResult(String name, boolean passed, String errorMessage, long durationMs) {
        this(name, passed, errorMessage, null, null, durationMs, 1);
    }

    /**
     * Full constructor including exception details for failed tests.
     *
     * @param name          display name of the test
     * @param passed        {@code true} if the test passed
     * @param errorMessage  root exception message, or {@code null}
     * @param exceptionType fully-qualified class name of the root exception, or {@code null}
     * @param stackTrace    full stack trace as a string, or {@code null}
     * @param durationMs    wall-clock execution time in milliseconds
     */
    public TestCaseResult(String name, boolean passed, String errorMessage,
                          String exceptionType, String stackTrace, long durationMs) {
        this(name, passed, errorMessage, exceptionType, stackTrace, durationMs, 1);
    }

    /**
     * Full constructor including attempt count — used when retries are configured.
     *
     * @param name          display name of the test
     * @param passed        {@code true} if the test passed
     * @param errorMessage  root exception message, or {@code null}
     * @param exceptionType fully-qualified class name of the root exception, or {@code null}
     * @param stackTrace    full stack trace as a string, or {@code null}
     * @param durationMs    total wall-clock time across all attempts in milliseconds
     * @param attempts      total number of attempts made (1 = no retries occurred)
     */
    public TestCaseResult(String name, boolean passed, String errorMessage,
                          String exceptionType, String stackTrace, long durationMs, int attempts) {
        this.name = name;
        this.passed = passed;
        this.errorMessage = errorMessage;
        this.exceptionType = exceptionType;
        this.stackTrace = stackTrace;
        this.durationMs = durationMs;
        this.attempts = attempts;
    }

    /** @return the display name of the test case */
    public String getName() { return name; }

    /** @return {@code true} if the test completed without throwing */
    public boolean isPassed() { return passed; }

    /** @return the root exception message on failure, or {@code null} on success */
    public String getErrorMessage() { return errorMessage; }

    /**
     * @return the fully-qualified class name of the root exception
     *         (e.g. {@code "java.lang.AssertionError"}), or {@code null} if passed
     */
    public String getExceptionType() { return exceptionType; }

    /**
     * @return the full stack trace as a multi-line string,
     *         or {@code null} for passing tests and timeouts
     */
    public String getStackTrace() { return stackTrace; }

    /** @return wall-clock execution time from test start to completion/failure/timeout */
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    /**
     * @return total number of execution attempts made; {@code 1} means the test
     *         passed or failed on the first try with no retries
     */
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
}
