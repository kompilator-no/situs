package no.testframework.javalibrary.runtime;

import no.testframework.javalibrary.annotations.RuntimeTestSuite;
import no.testframework.javalibrary.annotations.RunTimeTest;
import no.testframework.javalibrary.domain.TestCaseDefinition;
import no.testframework.javalibrary.domain.TestSuiteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Discovers and registers runtime test suites from a set of candidate classes.
 *
 * <p>Scans each candidate class for the {@link no.testframework.javalibrary.annotations.RuntimeTestSuite @RuntimeTestSuite}
 * annotation. For every annotated class it reads all methods carrying
 * {@link no.testframework.javalibrary.annotations.RunTimeTest @RunTimeTest}
 * and builds a {@link TestSuiteDefinition} describing the suite and its test cases.
 *
 * <p>This class is stateless — the same instance can be reused across calls.
 *
 * @see ClasspathScanner
 * @see no.testframework.javalibrary.spring.TestFrameworkService
 */
public class TestSuiteRegistry {

    private static final Logger log = LoggerFactory.getLogger(TestSuiteRegistry.class);

    /**
     * Scans {@code candidateClasses} and returns a {@link TestSuiteDefinition} for every
     * class annotated with {@code @RuntimeTestSuite}.
     *
     * <p>Classes that do not carry the annotation are silently ignored.
     * The order of the returned list matches the iteration order of {@code candidateClasses}.
     *
     * @param candidateClasses the set of classes to inspect — typically the result of
     *                         {@link ClasspathScanner#findAllRuntimeTestSuites()} or
     *                         {@link ClasspathScanner#findRuntimeTestSuites(String)}
     * @return a list of discovered suite definitions; empty if no annotated classes are found
     */
    public List<TestSuiteDefinition> getAllSuites(Set<Class<?>> candidateClasses) {
        log.debug("Scanning {} candidate classes for @RuntimeTestSuite", candidateClasses.size());
        List<TestSuiteDefinition> suites = new ArrayList<>();
        for (Class<?> clazz : candidateClasses) {
            if (clazz.isAnnotationPresent(RuntimeTestSuite.class)) {
                RuntimeTestSuite suiteAnn = clazz.getAnnotation(RuntimeTestSuite.class);
                List<TestCaseDefinition> tests = new ArrayList<>();
                for (Method m : clazz.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(RunTimeTest.class)) {
                        RunTimeTest testAnn = m.getAnnotation(RunTimeTest.class);
                        String testName = testAnn.name().isEmpty() ? m.getName() : testAnn.name();
                        log.debug("  Discovered test: '{}' in suite '{}'", testName, clazz.getSimpleName());
                        tests.add(new TestCaseDefinition(testName, testAnn.description(), m,
                                testAnn.timeoutMs(), testAnn.delayMs(), testAnn.retries()));
                    }
                }
                String suiteName = suiteAnn.name().isEmpty() ? clazz.getSimpleName() : suiteAnn.name();
                log.info("Discovered suite: '{}' with {} test(s) [parallel={}]", suiteName, tests.size(), suiteAnn.parallel());
                suites.add(new TestSuiteDefinition(suiteName, suiteAnn.description(), clazz, tests, suiteAnn.parallel()));
            }
        }
        log.info("Total suites discovered: {}", suites.size());
        return suites;
    }
}
