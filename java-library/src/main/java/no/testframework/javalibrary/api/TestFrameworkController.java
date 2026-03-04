package no.testframework.javalibrary.api;

import no.testframework.javalibrary.api.model.SuiteRunStatus;
import no.testframework.javalibrary.api.model.TestSuite;
import no.testframework.javalibrary.api.service.TestFrameworkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Predefined Spring controller that can be reused directly in consuming apps.
 * All test-run endpoints are async by default — they return a runId immediately
 * and the caller polls GET /runs/{runId}/status for progress and results.
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

    /** Start a suite run by body — returns runId immediately. */
    @PostMapping("/suites/run")
    public Map<String, String> runSuite(@RequestBody TestSuite suite) {
        return Map.of("runId", testFrameworkService.startSuiteAsync(suite.getName()));
    }

    /** Start a suite run by path — returns runId immediately. */
    @PostMapping("/suites/{suiteName}/run")
    public Map<String, String> runSuiteByName(@PathVariable String suiteName) {
        return Map.of("runId", testFrameworkService.startSuiteAsync(suiteName));
    }

    /** Start a single-test run — returns runId immediately. */
    @PostMapping("/suites/{suiteName}/tests/{testName}/run")
    public Map<String, String> runSingleTest(@PathVariable String suiteName, @PathVariable String testName) {
        return Map.of("runId", testFrameworkService.startSingleTestAsync(suiteName, testName));
    }

    /** Poll the live status of any run. */
    @GetMapping("/runs/{runId}/status")
    public SuiteRunStatus getRunStatus(@PathVariable String runId) {
        return testFrameworkService.getRunStatus(runId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }
}
