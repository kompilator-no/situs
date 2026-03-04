package no.testframework.javalibrary.api;

import no.testframework.javalibrary.api.model.TestCase;
import no.testframework.javalibrary.api.model.TestCaseResult;
import no.testframework.javalibrary.api.model.TestSuite;
import no.testframework.javalibrary.api.model.TestSuiteResult;
import no.testframework.javalibrary.api.service.TestFrameworkService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Predefined Spring controller that can be reused directly in consuming apps.
 */
@RestController
@RequestMapping("/api/test-framework")
public final class TestFrameworkController {

    private final TestFrameworkService testFrameworkService;

    public TestFrameworkController(TestFrameworkService testFrameworkService) {
        this.testFrameworkService = testFrameworkService;
    }

    @GetMapping("/status")
    public String getStatus() {
        return "OK";
    }

    @GetMapping("/suites")
    public List<TestSuite> getSuites() {
        return testFrameworkService.getAllSuites();
    }

    @PostMapping("/suites/run")
    public TestSuiteResult runSuite(@RequestBody TestSuite suite) {
        return testFrameworkService.runSuite(suite.getName());
    }

    @GetMapping("/suites/{suiteName}/run")
    public TestSuiteResult runSuiteByName(@PathVariable String suiteName) {
        return testFrameworkService.runSuite(suiteName);
    }

    @GetMapping("/suites/{suiteName}/tests/{testName}/run")
    public TestCaseResult runSingleTest(@PathVariable String suiteName, @PathVariable String testName) {
        return testFrameworkService.runSingleTest(suiteName, testName);
    }
}

