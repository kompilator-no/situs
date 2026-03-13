package no.kompilator.situs.runtime;

import no.kompilator.situs.annotations.TestSuite;
import no.kompilator.situs.domain.TestCaseDefinition;
import no.kompilator.situs.domain.TestSuiteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Discovers and registers system integration test suites from a set of candidate classes.
 *
 * <p>Scans each candidate class for the {@link no.kompilator.situs.annotations.TestSuite @TestSuite}
 * annotation. For every annotated class it reads all methods carrying
 * {@link no.kompilator.situs.annotations.Test @Test}
 * and builds a {@link TestSuiteDefinition} describing the suite and its test cases.
 *
 * <p>This class is stateless — the same instance can be reused across calls.
 *
 * @see ClasspathScanner
 * @see no.kompilator.situs.service.TestFrameworkService
 */
public class TestSuiteRegistry {

    private static final Logger log = LoggerFactory.getLogger(TestSuiteRegistry.class);
    private final TestDefinitionResolver testDefinitionResolver = new TestDefinitionResolver();

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
        List<TestCaseDefinition> tests = testDefinitionResolver.resolveTestCases(clazz);
        String suiteName = suiteAnn.name().isEmpty() ? clazz.getSimpleName() : suiteAnn.name();
        validateDuplicateTestNames(clazz, suiteName, tests);
        log.info("Discovered suite: '{}' with {} test(s) [parallel={}]", suiteName, tests.size(), suiteAnn.parallel());
        return new TestSuiteDefinition(suiteName, suiteAnn.description(), clazz, tests, suiteAnn.parallel());
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

}
