package no.testframework.javalibrary.api.model;

public class TestCaseResult {
    private String name;
    private boolean passed;
    private String errorMessage;
    private String exceptionType;
    private String stackTrace;
    private long durationMs;

    public TestCaseResult() {}

    public TestCaseResult(String name, boolean passed, String errorMessage, long durationMs) {
        this(name, passed, errorMessage, null, null, durationMs);
    }

    public TestCaseResult(String name, boolean passed, String errorMessage,
                          String exceptionType, String stackTrace, long durationMs) {
        this.name = name;
        this.passed = passed;
        this.errorMessage = errorMessage;
        this.exceptionType = exceptionType;
        this.stackTrace = stackTrace;
        this.durationMs = durationMs;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }

    public String getStackTrace() { return stackTrace; }
    public void setStackTrace(String stackTrace) { this.stackTrace = stackTrace; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
