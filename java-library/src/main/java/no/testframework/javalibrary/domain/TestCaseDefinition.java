package no.testframework.javalibrary.domain;

import java.lang.reflect.Method;

public class TestCaseDefinition {

    private final String name;
    private final String description;
    private final Method method;

    public TestCaseDefinition(String name, String description, Method method) {
        this.name = name;
        this.description = description;
        this.method = method;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Method getMethod() { return method; }
}
