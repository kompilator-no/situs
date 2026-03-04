package no.testframework.javalibrary.domain;

import java.lang.reflect.Method;

public class TestCaseDefinition {

    private final String name;
    private final String description;
    private final Method method;
    private final long timeoutMs;
    private final long delayMs;

    public TestCaseDefinition(String name, String description, Method method, long timeoutMs, long delayMs) {
        this.name = name;
        this.description = description;
        this.method = method;
        this.timeoutMs = timeoutMs;
        this.delayMs = delayMs;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Method getMethod() { return method; }
    /** Returns the configured timeout in ms. 0 = use framework default (10 s). -1 = no timeout. */
    public long getTimeoutMs() { return timeoutMs; }
    /** Returns the delay in ms to wait before this test starts. 0 = no delay. */
    public long getDelayMs() { return delayMs; }
}
