package no.kompilator.javalibrary.spring;

import no.kompilator.javalibrary.model.TestSuite;
import no.kompilator.javalibrary.model.SuiteRunStatus;
import no.kompilator.javalibrary.service.AlreadyRunningException;
import no.kompilator.javalibrary.service.TestFrameworkService;
import no.kompilator.javalibrary.spring.model.RunSuiteRequest;
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
 * Spring REST controller that exposes the test-framework over HTTP.
 *
 * <p>All run endpoints are <b>asynchronous</b>: they return a {@code runId} immediately;
 * poll {@code GET /api/test-framework/runs/{runId}/status} until
 * {@code status == "COMPLETED"} to retrieve results.
 *
 * <p>Registered automatically as a Spring bean by {@link RuntimeTestAutoConfiguration}.
 * In Spring Boot apps this happens without any extra configuration — just add the
 * library JAR to the classpath. For non-Boot Spring apps add
 * {@link EnableRuntimeTests @EnableRuntimeTests} to any {@code @Configuration} class.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET  /api/test-framework/status}                              — health check</li>
 *   <li>{@code GET  /api/test-framework/suites}                              — list all suites</li>
 *   <li>{@code POST /api/test-framework/suites/run/by-name}                  — run suite (body)</li>
 *   <li>{@code POST /api/test-framework/suites/{suiteName}/run}              — run suite by name</li>
 *   <li>{@code POST /api/test-framework/suites/{suiteName}/tests/{name}/run} — run single test</li>
 *   <li>{@code GET  /api/test-framework/runs/{runId}/status}                 — poll run status</li>
 * </ul>
 *
 * <h2>Error responses</h2>
 * <ul>
 *   <li>{@code 404 Not Found} — suite or test name not recognised</li>
 *   <li>{@code 409 Conflict}  — suite/test is already running</li>
 * </ul>
 *
 * @see TestFrameworkService
 * @see RuntimeTestAutoConfiguration
 */
@RestController
@RequestMapping("/api/test-framework")
public final class TestFrameworkController {

    private final TestFrameworkService testFrameworkService;

    /**
     * @param testFrameworkService the service that manages suite discovery and execution
     */
    public TestFrameworkController(TestFrameworkService testFrameworkService) {
        this.testFrameworkService = testFrameworkService;
    }

    /**
     * Health-check endpoint.
     *
     * @return {@code "OK"} when the application is running
     */
    @GetMapping("/status")
    public String getStatus() {
        return "OK";
    }

    /**
     * Returns all registered suites and their test cases.
     *
     * @return list of {@link TestSuite} descriptors
     */
    @GetMapping("/suites")
    public List<TestSuite> getSuites() {
        return testFrameworkService.getAllSuites();
    }

    /**
     * Starts a suite run using the suite name from the request body.
     *
     * <p>Use this endpoint when the suite name is determined at runtime.
     * The body only requires the {@code name} field:
     * <pre>{@code {"name": "CalculatorTestSuite"} }</pre>
     *
     * @param request request body containing the suite {@code name}
     * @return map containing {@code runId}
     */
    @PostMapping("/suites/run/by-name")
    public Map<String, String> runSuite(@RequestBody RunSuiteRequest request) {
        return Map.of("runId", testFrameworkService.startSuiteAsync(request.name()));
    }

    /**
     * Starts a suite run by path name.
     *
     * @param suiteName the suite name as registered in {@code @TestSuite#name()}
     * @return map containing {@code runId}
     */
    @PostMapping("/suites/{suiteName}/run")
    public Map<String, String> runSuiteByName(@PathVariable String suiteName) {
        return Map.of("runId", testFrameworkService.startSuiteAsync(suiteName));
    }

    /**
     * Starts a single test within a suite asynchronously.
     *
     * @param suiteName the suite containing the test
     * @param testName  the test name as registered in {@code @Test#name()}
     * @return map containing {@code runId}
     */
    @PostMapping("/suites/{suiteName}/tests/{testName}/run")
    public Map<String, String> runSingleTest(@PathVariable String suiteName,
                                              @PathVariable String testName) {
        return Map.of("runId", testFrameworkService.startSingleTestAsync(suiteName, testName));
    }

    /**
     * Returns the current status snapshot for the given run.
     *
     * @param runId the run identifier returned by a start-run endpoint
     * @return the live {@link SuiteRunStatus} snapshot
     */
    @GetMapping("/runs/{runId}/status")
    public SuiteRunStatus getRunStatus(@PathVariable String runId) {
        return testFrameworkService.getRunStatus(runId);
    }

    /**
     * Handles unknown suite or test names — returns {@code 404 Not Found}.
     *
     * @param ex the exception carrying the error message
     * @return a JSON body {@code {"error": "..."}} with status 404
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Handles duplicate run attempts — returns {@code 409 Conflict}.
     *
     * @param ex the exception carrying the error message
     * @return a JSON body {@code {"error": "..."}} with status 409
     */
    @ExceptionHandler(AlreadyRunningException.class)
    public ResponseEntity<Map<String, String>> handleAlreadyRunning(AlreadyRunningException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }
}
