package no.certusdev.testframework.javalibrary.runtime;

import no.certusdev.testframework.javalibrary.annotations.AfterAll;
import no.certusdev.testframework.javalibrary.annotations.AfterEach;
import no.certusdev.testframework.javalibrary.annotations.BeforeAll;
import no.certusdev.testframework.javalibrary.annotations.BeforeEach;
import no.certusdev.testframework.javalibrary.annotations.TestSuite;
import no.certusdev.testframework.javalibrary.annotations.Test;
import no.certusdev.testframework.javalibrary.domain.TestCaseDefinition;
import no.certusdev.testframework.javalibrary.domain.TestSuiteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Discovers and registers runtime test suites from a set of candidate classes.
 *
 * <p>Scans each candidate class for the {@link no.certusdev.testframework.javalibrary.annotations.TestSuite @TestSuite}
 * annotation. For every annotated class it reads all methods carrying
 * {@link no.certusdev.testframework.javalibrary.annotations.Test @Test}
 * and builds a {@link TestSuiteDefinition} describing the suite and its test cases.
 *
 * <p>This class is stateless — the same instance can be reused across calls.
 *
 * @see ClasspathScanner
 * @see no.certusdev.testframework.javalibrary.service.TestFrameworkService
 */
public class TestSuiteRegistry {

    private static final Logger log = LoggerFactory.getLogger(TestSuiteRegistry.class);

    /**
     * Scans {@code candidateClasses} and returns a {@link TestSuiteDefinition} for every
     * class annotated with {@code @TestSuite}.
     *
     * <p>Classes that do not carry the annotation are silently ignored.
     * The order of the returned list matches the iteration order of {@code candidateClasses}.
     *
     * @param candidateClasses the set of classes to inspect — typically the result of
     *                         {@link ClasspathScanner#findAllTestSuites()} or
     *                         {@link ClasspathScanner#findTestSuites(String)}
     * @return a list of discovered suite definitions; empty if no annotated classes are found
     */
    public List<TestSuiteDefinition> getAllSuites(Set<Class<?>> candidateClasses) {
        log.debug("Scanning {} candidate classes for @TestSuite", candidateClasses.size());
        List<TestSuiteDefinition> suites = candidateClasses.stream()
                .filter(clazz -> clazz.isAnnotationPresent(TestSuite.class))
                .map(this::toSuiteDefinition)
                .toList();
        validateDuplicateSuiteNames(suites);

        log.info("Total suites discovered: {}", suites.size());
        return suites;
    }

    private TestSuiteDefinition toSuiteDefinition(Class<?> clazz) {
        TestSuite suiteAnn = clazz.getAnnotation(TestSuite.class);
        validateLifecycleMethods(clazz);
        List<TestCaseDefinition> tests = java.util.Arrays.stream(clazz.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Test.class))
                .sorted(Comparator
                        .comparingInt((Method method) -> method.getAnnotation(Test.class).order())
                        .thenComparing(Method::getName))
                .map(method -> toTestCaseDefinition(clazz, method))
                .collect(Collectors.toList());
        String suiteName = suiteAnn.name().isEmpty() ? clazz.getSimpleName() : suiteAnn.name();
        validateDuplicateTestNames(clazz, suiteName, tests);
        log.info("Discovered suite: '{}' with {} test(s) [parallel={}]", suiteName, tests.size(), suiteAnn.parallel());
        return new TestSuiteDefinition(suiteName, suiteAnn.description(), clazz, tests, suiteAnn.parallel());
    }

    private TestCaseDefinition toTestCaseDefinition(Class<?> clazz, Method method) {
        Test testAnn = method.getAnnotation(Test.class);
        String testName = testAnn.name().isEmpty() ? method.getName() : testAnn.name();
        validateAnnotatedMethodShape(clazz, method, "@Test");
        validateTestConfiguration(clazz, testName, testAnn);
        log.debug("  Discovered test: '{}' in suite '{}'", testName, clazz.getSimpleName());
        return new TestCaseDefinition(testName, testAnn.description(), method,
                testAnn.timeoutMs(), testAnn.delayMs(), testAnn.retries());
    }

    private void validateDuplicateSuiteNames(List<TestSuiteDefinition> suites) {
        Set<String> duplicateNames = findDuplicateNames(suites.stream()
                .map(TestSuiteDefinition::getName)
                .toList());
        if (!duplicateNames.isEmpty()) {
            throw new IllegalArgumentException("Duplicate suite name(s) found: " + String.join(", ", duplicateNames));
        }
    }

    private void validateDuplicateTestNames(Class<?> suiteClass, String suiteName, List<TestCaseDefinition> tests) {
        Set<String> duplicateNames = findDuplicateNames(tests.stream()
                .map(TestCaseDefinition::getName)
                .toList());
        if (!duplicateNames.isEmpty()) {
            throw new IllegalArgumentException(
                    "Duplicate test name(s) found in suite '" + suiteName + "' (" + suiteClass.getName() + "): "
                            + String.join(", ", duplicateNames));
        }
    }

    private Set<String> findDuplicateNames(List<String> names) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (String name : names) {
            if (!seen.add(name)) {
                duplicates.add(name);
            }
        }
        return duplicates;
    }

    private void validateTestConfiguration(Class<?> suiteClass, String testName, Test testAnnotation) {
        if (testAnnotation.timeoutMs() < -1) {
            throw new IllegalArgumentException("Invalid timeoutMs for test '" + testName + "' in suite '"
                    + suiteClass.getName() + "': " + testAnnotation.timeoutMs() + " (must be -1, 0, or > 0)");
        }
        if (testAnnotation.delayMs() < 0) {
            throw new IllegalArgumentException("Invalid delayMs for test '" + testName + "' in suite '"
                    + suiteClass.getName() + "': " + testAnnotation.delayMs() + " (must be >= 0)");
        }
        if (testAnnotation.retries() < 0) {
            throw new IllegalArgumentException("Invalid retries for test '" + testName + "' in suite '"
                    + suiteClass.getName() + "': " + testAnnotation.retries() + " (must be >= 0)");
        }
    }

    private void validateLifecycleMethods(Class<?> suiteClass) {
        validateLifecycleMethods(suiteClass, BeforeAll.class, "@BeforeAll");
        validateLifecycleMethods(suiteClass, BeforeEach.class, "@BeforeEach");
        validateLifecycleMethods(suiteClass, AfterEach.class, "@AfterEach");
        validateLifecycleMethods(suiteClass, AfterAll.class, "@AfterAll");
    }

    private void validateLifecycleMethods(
            Class<?> suiteClass, Class<? extends java.lang.annotation.Annotation> annotationType, String label) {
        java.util.Arrays.stream(suiteClass.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(annotationType))
                .forEach(method -> validateAnnotatedMethodShape(suiteClass, method, label));
    }

    private void validateAnnotatedMethodShape(Class<?> suiteClass, Method method, String annotationLabel) {
        if (!Modifier.isPublic(method.getModifiers())) {
            throw new IllegalArgumentException(annotationLabel + " method '" + method.getName() + "' in suite '"
                    + suiteClass.getName() + "' must be public");
        }
        if (Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException(annotationLabel + " method '" + method.getName() + "' in suite '"
                    + suiteClass.getName() + "' must not be static");
        }
        if (method.getParameterCount() != 0) {
            throw new IllegalArgumentException(annotationLabel + " method '" + method.getName() + "' in suite '"
                    + suiteClass.getName() + "' must not declare parameters");
        }
    }
}
