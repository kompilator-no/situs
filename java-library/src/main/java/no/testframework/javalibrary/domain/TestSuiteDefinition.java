package no.testframework.javalibrary.domain;

import java.util.List;

/**
 * Immutable descriptor for a test suite discovered during classpath scanning.
 *
 * <p>Carries everything needed to execute a suite:
 * <ul>
 *   <li>the display name and optional description</li>
 *   <li>the annotated class (used to create fresh instances per test run)</li>
 *   <li>the ordered list of {@link TestCaseDefinition}s found on the class</li>
 *   <li>the execution mode ({@code parallel} flag)</li>
 * </ul>
 *
 * <p>Instances are created by
 * {@link no.testframework.javalibrary.runtime.TestSuiteRegistry#getAllSuites(java.util.Set)}
 * and consumed by {@link no.testframework.javalibrary.spring.TestFrameworkService}
 * and {@link no.testframework.javalibrary.runtime.TestRunner}.
 */
public class TestSuiteDefinition {

    private final String name;
    private final String description;
    private final Class<?> suiteClass;
    private final List<TestCaseDefinition> testCases;
    private final boolean parallel;

    /**
     * Creates a new suite descriptor.
     *
     * @param name       display name (from {@code @RuntimeTestSuite#name()}, or the class simple name)
     * @param description optional human-readable description of the suite
     * @param suiteClass the annotated class; instantiated fresh for each test run
     * @param testCases  ordered list of discovered test case definitions
     * @param parallel   {@code true} if all tests in this suite should run concurrently
     */
    public TestSuiteDefinition(String name, String description, Class<?> suiteClass,
                               List<TestCaseDefinition> testCases, boolean parallel) {
        this.name = name;
        this.description = description;
        this.suiteClass = suiteClass;
        this.testCases = testCases;
        this.parallel = parallel;
    }

    /** @return the display name of this suite */
    public String getName() { return name; }

    /** @return the optional description, may be empty but never {@code null} */
    public String getDescription() { return description; }

    /** @return the suite class to instantiate when running tests */
    public Class<?> getSuiteClass() { return suiteClass; }

    /** @return the ordered list of test case definitions discovered in this suite */
    public List<TestCaseDefinition> getTestCases() { return testCases; }

    /**
     * @return {@code true} if the tests in this suite should be executed in parallel;
     *         {@code false} for sequential execution (default)
     */
    public boolean isParallel() { return parallel; }
}
