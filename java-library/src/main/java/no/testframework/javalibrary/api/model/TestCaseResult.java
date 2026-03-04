package no.testframework.javalibrary.api.model;

public class TestCaseResult {
    private String name;
    private boolean passed;
    private String errorMessage;
    private long durationMs;

    public TestCaseResult() {}

    public TestCaseResult(String name, boolean passed, String errorMessage, long durationMs) {
        this.name = name;
        this.passed = passed;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
