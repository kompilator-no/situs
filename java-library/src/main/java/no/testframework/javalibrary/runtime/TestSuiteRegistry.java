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

public class TestSuiteRegistry {

    private static final Logger log = LoggerFactory.getLogger(TestSuiteRegistry.class);

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
                        tests.add(new TestCaseDefinition(testName, testAnn.description(), m, testAnn.timeoutMs(), testAnn.delayMs()));
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




