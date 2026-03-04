package no.testframework.javalibrary.api.model;

import java.util.List;

public class TestSuite {
    private String name;
    private String description;
    private List<TestCase> tests;

    public TestSuite() {}

    public TestSuite(String name, String description, List<TestCase> tests) {
        this.name = name;
        this.description = description;
        this.tests = tests;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<TestCase> getTests() { return tests; }
    public void setTests(List<TestCase> tests) { this.tests = tests; }
}
