package no.testframework.javalibrary.api.service;

import no.testframework.javalibrary.api.model.TestCase;
import no.testframework.javalibrary.api.model.TestCaseResult;
import no.testframework.javalibrary.api.model.TestSuite;
import no.testframework.javalibrary.api.model.TestSuiteResult;
import no.testframework.javalibrary.domain.TestCaseDefinition;
import no.testframework.javalibrary.domain.TestCaseExecutionResult;
import no.testframework.javalibrary.domain.TestSuiteDefinition;
import no.testframework.javalibrary.domain.TestSuiteExecutionResult;
import no.testframework.javalibrary.runtime.RuntimeTestSuiteRunner;
import no.testframework.javalibrary.runtime.TestRunner;
import no.testframework.javalibrary.runtime.TestSuiteRegistry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TestFrameworkService {

    private final TestSuiteRegistry registry;
    private final RuntimeTestSuiteRunner suiteRunner;
    private final TestRunner testRunner;
    private final Set<Class<?>> registeredSuites;

    public TestFrameworkService(Set<Class<?>> registeredSuites) {
        this.registry = new TestSuiteRegistry();
        this.suiteRunner = new RuntimeTestSuiteRunner();
        this.testRunner = new TestRunner();
        this.registeredSuites = registeredSuites;
    }

    public List<TestSuite> getAllSuites() {
        return registry.getAllSuites(registeredSuites).stream()
            .map(this::toTestSuite)
            .collect(Collectors.toList());
    }

    public TestSuiteResult runSuite(String suiteName) {
        TestSuiteDefinition definition = findSuite(suiteName);
        TestSuiteExecutionResult result = suiteRunner.runSuite(definition.getSuiteClass());
        return toTestSuiteResult(result);
    }

    public TestCaseResult runSingleTest(String suiteName, String testName) {
        TestSuiteDefinition suite = findSuite(suiteName);
        TestCaseDefinition testCase = suite.getTestCases().stream()
            .filter(t -> t.getName().equals(testName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Test not found: " + testName));
        List<TestCaseExecutionResult> results = testRunner.runTests(suite.getSuiteClass());
        return results.stream()
            .filter(r -> r.getName().equals(testCase.getName()))
            .findFirst()
            .map(r -> new TestCaseResult(r.getName(), r.isPassed(), r.getErrorMessage(), r.getDurationMs()))
            .orElseThrow(() -> new IllegalArgumentException("Test execution result not found: " + testName));
    }

    private TestSuiteDefinition findSuite(String suiteName) {
        return registry.getAllSuites(registeredSuites).stream()
            .filter(s -> s.getName().equals(suiteName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Suite not found: " + suiteName));
    }

    private TestSuite toTestSuite(TestSuiteDefinition definition) {
        List<TestCase> cases = definition.getTestCases().stream()
            .map(t -> new TestCase(t.getName(), t.getDescription()))
            .collect(Collectors.toList());
        return new TestSuite(definition.getName(), definition.getDescription(), cases);
    }

    private TestSuiteResult toTestSuiteResult(TestSuiteExecutionResult result) {
        List<TestCaseResult> caseResults = result.getTestCaseResults().stream()
            .map(r -> new TestCaseResult(r.getName(), r.isPassed(), r.getErrorMessage(), r.getDurationMs()))
            .collect(Collectors.toList());
        return new TestSuiteResult(result.getSuiteName(), result.getDescription(), caseResults,
            result.getPassedCount(), result.getFailedCount(), result.isAllPassed());
    }
}
