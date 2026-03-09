package no.testframework.javalibrary.runtime;

import no.testframework.javalibrary.annotations.TestSuite;
import no.testframework.javalibrary.domain.TestSuiteExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convenience facade that runs a single {@code @TestSuite}-annotated class
 * end-to-end and writes a formatted report via {@link SuiteReporter}.
 *
 * <p>Typical usage (standalone, no Spring required):
 * <pre>{@code
 * RuntimeTestSuiteRunner runner = new RuntimeTestSuiteRunner();
 * TestSuiteExecutionResult result = runner.runSuite(MyTestSuite.class);
 * }</pre>
 *
 * <p>This class is stateless and thread-safe — the same instance can run
 * multiple suites concurrently.
 *
 * @see TestRunner
 * @see SuiteReporter
 * @see no.testframework.javalibrary.service.TestFrameworkService
 */
public class RuntimeTestSuiteRunner {

    private static final Logger log = LoggerFactory.getLogger(RuntimeTestSuiteRunner.class);

    private final InstanceFactory instanceFactory;

    /**
     * Creates a runner that uses plain reflection to instantiate suite classes.
     * Use this when no DI container is available.
     */
    public RuntimeTestSuiteRunner() {
        this(InstanceFactory.reflective());
    }

    /**
     * Creates a runner that uses the given {@link InstanceFactory} to instantiate
     * suite classes. Pass a {@code SpringInstanceFactory} to enable Spring DI.
     *
     * @param instanceFactory the factory to use for creating suite instances
     */
    public RuntimeTestSuiteRunner(InstanceFactory instanceFactory) {
        this.instanceFactory = instanceFactory;
    }

    /**
     * Executes all {@code @Test} methods in {@code suiteClass}, writes a
     * formatted report to the logger, and returns the aggregated result.
     *
     * <p>The suite class must be annotated with {@code @TestSuite}; if it is
     * not, an {@link IllegalArgumentException} is thrown immediately without running
     * any tests.
     *
     * @param suiteClass the class to execute — must carry {@code @TestSuite}
     * @return a {@link no.testframework.javalibrary.domain.TestSuiteExecutionResult}
     *         containing individual test results and aggregate pass/fail counts
     * @throws IllegalArgumentException if {@code suiteClass} is not annotated with
     *                                  {@code @TestSuite}
     */
    public TestSuiteExecutionResult runSuite(Class<?> suiteClass) {
        if (!suiteClass.isAnnotationPresent(TestSuite.class)) {
            throw new IllegalArgumentException("Class is not annotated with @TestSuite");
        }
        TestSuite suiteAnnotation = suiteClass.getAnnotation(TestSuite.class);
        String suiteName   = suiteAnnotation.name().isEmpty() ? suiteClass.getSimpleName() : suiteAnnotation.name();
        String description = suiteAnnotation.description();

        log.debug("Starting suite: '{}'", suiteName);
        TestSuiteExecutionResult result = new TestSuiteExecutionResult(
                suiteName, description, new TestRunner(instanceFactory).runTests(suiteClass));

        SuiteReporter.report(suiteName, description, result.getTestCaseResults());
        return result;
    }
}
