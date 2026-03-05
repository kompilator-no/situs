package no.testframework.javalibrary.domain;

import java.util.List;

public class TestSuiteDefinition {

    private final String name;
    private final String description;
    private final Class<?> suiteClass;
    private final List<TestCaseDefinition> testCases;
    private final boolean parallel;

    public TestSuiteDefinition(String name, String description, Class<?> suiteClass,
                               List<TestCaseDefinition> testCases, boolean parallel) {
        this.name = name;
        this.description = description;
        this.suiteClass = suiteClass;
        this.testCases = testCases;
        this.parallel = parallel;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Class<?> getSuiteClass() { return suiteClass; }
    public List<TestCaseDefinition> getTestCases() { return testCases; }
    public boolean isParallel() { return parallel; }
}
