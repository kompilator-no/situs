package no.testframework.javalibrary.model;

import java.util.List;

/**
 * Descriptor of a test suite and its discovered test cases.
 *
 * <p>Part of the shared model layer ({@code no.testframework.javalibrary.model}) which
 * has no Spring dependency and can be used in any context.
 *
 * <p>Returned by {@link no.testframework.javalibrary.spring.TestFrameworkService#getAllSuites()}
 * and accepted as a JSON request body by the
 * {@link no.testframework.javalibrary.spring.TestFrameworkController} run endpoints.
 * Mutable fields with a no-arg constructor allow Jackson to deserialise this class
 * without extra configuration.
 */
public class TestSuite {

    private String name;
    private String description;
    private List<TestCase> tests;
    private boolean parallel;

    /** No-arg constructor required by Jackson for deserialisation. */
    public TestSuite() {}

    /**
     * @param name        display name of the suite
     * @param description optional human-readable description
     * @param tests       list of test case descriptors
     * @param parallel    {@code true} if the suite runs tests in parallel
     */
    public TestSuite(String name, String description, List<TestCase> tests, boolean parallel) {
        this.name = name;
        this.description = description;
        this.tests = tests;
        this.parallel = parallel;
    }

    /** @return the display name of this suite */
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    /** @return the optional description of this suite */
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** @return the test cases belonging to this suite */
    public List<TestCase> getTests() { return tests; }
    public void setTests(List<TestCase> tests) { this.tests = tests; }

    /**
     * @return {@code true} if this suite runs tests in parallel;
     *         {@code false} for sequential execution
     */
    public boolean isParallel() { return parallel; }
    public void setParallel(boolean parallel) { this.parallel = parallel; }
}
