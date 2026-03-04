package no.testframework.javalibrary.domain;

public class TestCaseExecutionResult {

    private final String name;
    private final boolean passed;
    private final String errorMessage;
    private final long durationMs;

    public TestCaseExecutionResult(String name, boolean passed, String errorMessage, long durationMs) {
        this.name = name;
        this.passed = passed;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
    }

    public String getName() { return name; }
    public boolean isPassed() { return passed; }
    public String getErrorMessage() { return errorMessage; }
    public long getDurationMs() { return durationMs; }
}
