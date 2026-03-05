package no.testframework.javalibrary.domain;

public class TestCaseExecutionResult {

    private final String name;
    private final boolean passed;
    private final String errorMessage;
    private final String exceptionType;
    private final String stackTrace;
    private final long durationMs;

    public TestCaseExecutionResult(String name, boolean passed, String errorMessage, long durationMs) {
        this(name, passed, errorMessage, null, null, durationMs);
    }

    public TestCaseExecutionResult(String name, boolean passed, String errorMessage,
                                   String exceptionType, String stackTrace, long durationMs) {
        this.name = name;
        this.passed = passed;
        this.errorMessage = errorMessage;
        this.exceptionType = exceptionType;
        this.stackTrace = stackTrace;
        this.durationMs = durationMs;
    }

    public String getName() { return name; }
    public boolean isPassed() { return passed; }
    public String getErrorMessage() { return errorMessage; }
    public String getExceptionType() { return exceptionType; }
    public String getStackTrace() { return stackTrace; }
    public long getDurationMs() { return durationMs; }
}
