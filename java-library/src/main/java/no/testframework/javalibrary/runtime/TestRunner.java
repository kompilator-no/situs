package no.testframework.javalibrary.runtime;

import no.testframework.javalibrary.annotations.RunTimeTest;
import no.testframework.javalibrary.domain.TestCaseExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class TestRunner {

    private static final Logger log = LoggerFactory.getLogger(TestRunner.class);

    public List<TestCaseExecutionResult> runTests(Class<?> testClass) {
        log.info("Running tests in class: {}", testClass.getName());
        List<TestCaseExecutionResult> results = new ArrayList<>();
        for (Method method : testClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(RunTimeTest.class)) {
                RunTimeTest annotation = method.getAnnotation(RunTimeTest.class);
                String testName = annotation.name().isEmpty() ? method.getName() : annotation.name();
                log.debug("Executing test: {}", testName);
                long start = System.currentTimeMillis();
                try {
                    method.setAccessible(true);
                    Object instance = testClass.getDeclaredConstructor().newInstance();
                    method.invoke(instance);
                    long duration = System.currentTimeMillis() - start;
                    log.info("  [PASSED] {} ({}ms)", testName, duration);
                    results.add(new TestCaseExecutionResult(testName, true, null, duration));
                } catch (Throwable t) {
                    long duration = System.currentTimeMillis() - start;
                    Throwable cause = t.getCause() != null ? t.getCause() : t;
                    log.warn("  [FAILED] {} ({}ms) - {}", testName, duration, cause.getMessage());
                    results.add(new TestCaseExecutionResult(testName, false, cause.getMessage(), duration));
                }
            }
        }
        return results;
    }
}




