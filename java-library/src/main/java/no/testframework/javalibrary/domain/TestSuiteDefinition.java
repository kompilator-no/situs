package no.testframework.javalibrary.domain;

import java.util.List;

public class TestSuiteDefinition {

    private final String name;
    private final String description;
    private final Class<?> suiteClass;
    private final List<TestCaseDefinition> testCases;

    public TestSuiteDefinition(String name, String description, Class<?> suiteClass, List<TestCaseDefinition> testCases) {
        this.name = name;
        this.description = description;
        this.suiteClass = suiteClass;
        this.testCases = testCases;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Class<?> getSuiteClass() { return suiteClass; }
    public List<TestCaseDefinition> getTestCases() { return testCases; }
}
