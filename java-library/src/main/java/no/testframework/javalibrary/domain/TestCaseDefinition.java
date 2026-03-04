package no.testframework.javalibrary.domain;

import java.lang.reflect.Method;

public class TestCaseDefinition {

    private final String name;
    private final String description;
    private final Method method;
    private final long timeoutMs;

    public TestCaseDefinition(String name, String description, Method method, long timeoutMs) {
        this.name = name;
        this.description = description;
        this.method = method;
        this.timeoutMs = timeoutMs;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Method getMethod() { return method; }
    /** Returns the timeout in milliseconds. 0 means no timeout. */
    public long getTimeoutMs() { return timeoutMs; }
}
