package no.testframework.javalibrary.runtime;

import no.testframework.javalibrary.annotations.RuntimeTestSuite;
import no.testframework.javalibrary.domain.TestSuiteExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuntimeTestSuiteRunner {

    private static final Logger log = LoggerFactory.getLogger(RuntimeTestSuiteRunner.class);

    public TestSuiteExecutionResult runSuite(Class<?> suiteClass) {
        if (!suiteClass.isAnnotationPresent(RuntimeTestSuite.class)) {
            throw new IllegalArgumentException("Class is not annotated with @RuntimeTestSuite");
        }
        RuntimeTestSuite suiteAnnotation = suiteClass.getAnnotation(RuntimeTestSuite.class);
        String suiteName    = suiteAnnotation.name().isEmpty() ? suiteClass.getSimpleName() : suiteAnnotation.name();
        String description  = suiteAnnotation.description();

        log.debug("Starting suite: '{}'", suiteName);
        TestSuiteExecutionResult result = new TestSuiteExecutionResult(
                suiteName, description, new TestRunner().runTests(suiteClass));

        SuiteReporter.report(suiteName, description, result.getTestCaseResults());
        return result;
    }
}
