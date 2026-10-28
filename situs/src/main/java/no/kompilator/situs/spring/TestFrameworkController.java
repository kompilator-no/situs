package no.kompilator.situs.spring;

import no.kompilator.situs.model.TestSuite;
import no.kompilator.situs.model.SuiteRunStatus;
import no.kompilator.situs.service.AlreadyRunningException;
import no.kompilator.situs.service.TestFrameworkService;
import no.kompilator.situs.spring.model.RunSuiteRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Spring REST controller that exposes Situs over HTTP.
 *
 * <p>Run endpoints are <b>asynchronous</b> by default: they return a {@code runId} immediately;
 * poll {@code GET /api/test-framework/runs/{runId}/status} until
 * {@code status == "COMPLETED"} to retrieve results. The
 * {@code POST /api/test-framework/suites/{suiteName}/gate} endpoint instead waits for the suite
 * to finish and returns its outcome as the HTTP status, which suits deployment pipelines.
 *
 * <p>Registered automatically as a Spring bean by {@link RuntimeTestAutoConfiguration}.
 * In Spring Boot apps this happens without any extra configuration — just add the
 * library JAR to the classpath. For non-Boot Spring apps add
 * {@link EnableRuntimeTests @EnableRuntimeTests} to any {@code @Configuration} class.
 *
 * <h2>Authentication</h2>
 * <p>When an API token is configured ({@code testframework.api-token}), every endpoint except
 * {@code /status} requires the header {@code Authorization: Bearer <token>}. Without a token the
 * API is open.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET  /api/test-framework/status}                              — health check (never authenticated)</li>
 *   <li>{@code GET  /api/test-framework/suites}                              — list all suites</li>
 *   <li>{@code POST /api/test-framework/suites/run/by-name}                  — run suite (body)</li>
 *   <li>{@code POST /api/test-framework/suites/{suiteName}/run}              — run suite by name</li>
 *   <li>{@code POST /api/test-framework/suites/{suiteName}/gate}             — run suite and wait for the outcome</li>
 *   <li>{@code POST /api/test-framework/suites/{suiteName}/tests/{name}/run} — run single test</li>
 *   <li>{@code GET  /api/test-framework/runs/{runId}/status}                 — poll run status</li>
 *   <li>{@code POST /api/test-framework/runs/{runId}/cancel}                 — cancel a running run</li>
 * </ul>
 *
 * <h2>Gate responses</h2>
 * <ul>
 *   <li>{@code 200 OK} — the suite completed and every test passed</li>
 *   <li>{@code 422 Unprocessable Content} — the suite completed with one or more failing tests</li>
 *   <li>{@code 500 Internal Server Error} — the run itself failed</li>
 *   <li>{@code 504 Gateway Timeout} — the suite did not finish within {@code timeout}; the run is cancelled</li>
 * </ul>
 * Every gate response body is the {@link SuiteRunStatus} of the run.
 *
 * <h2>Error responses</h2>
 * <ul>
 *   <li>{@code 401 Unauthorized} — API token missing or wrong</li>
 *   <li>{@code 404 Not Found} — suite, test or run not recognised</li>
 *   <li>{@code 409 Conflict}  — suite/test is already running</li>
 * </ul>
 *
 * @see TestFrameworkService
 * @see RuntimeTestAutoConfiguration
 */
@RestController
@RequestMapping("/api/test-framework")
public final class TestFrameworkController {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String DEFAULT_GATE_TIMEOUT = "PT5M";

    private final TestFrameworkService testFrameworkService;
    private final String apiToken;

    /**
     * Creates a controller without authentication.
     *
     * @param testFrameworkService the service that manages suite discovery and execution
     */
    public TestFrameworkController(TestFrameworkService testFrameworkService) {
        this(testFrameworkService, null);
    }

    /**
     * @param testFrameworkService the service that manages suite discovery and execution
     * @param apiToken             shared secret required as a bearer token on every endpoint except
     *                             {@code /status}; {@code null} or blank disables the check
     */
    public TestFrameworkController(TestFrameworkService testFrameworkService, String apiToken) {
        this.testFrameworkService = testFrameworkService;
        this.apiToken = apiToken == null || apiToken.isBlank() ? null : apiToken;
    }

    /**
     * Health-check endpoint. Never requires authentication, so load balancers and probes can use it.
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
     * @param authorization the {@code Authorization} header, if any
     * @return list of {@link TestSuite} descriptors
     */
    @GetMapping("/suites")
    public List<TestSuite> getSuites(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authorize(authorization);
        return testFrameworkService.getAllSuites();
    }

    /**
     * Starts a suite run using the suite name from the request body.
     *
     * <p>Use this endpoint when the suite name is determined at runtime.
     * The body only requires the {@code name} field:
     * <pre>{@code {"name": "CalculatorTestSuite"} }</pre>
     *
     * @param request       request body containing the suite {@code name}
     * @param authorization the {@code Authorization} header, if any
     * @return map containing {@code runId}
     */
    @PostMapping("/suites/run/by-name")
    public Map<String, String> runSuite(@RequestBody RunSuiteRequest request,
                                        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authorize(authorization);
        return Map.of("runId", testFrameworkService.startSuiteAsync(request.name()));
    }

    /**
     * Starts a suite run by path name.
     *
     * @param suiteName     the suite name as registered in {@code @TestSuite#name()}
     * @param authorization the {@code Authorization} header, if any
     * @return map containing {@code runId}
     */
    @PostMapping("/suites/{suiteName}/run")
    public Map<String, String> runSuiteByName(@PathVariable String suiteName,
                                              @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authorize(authorization);
        return Map.of("runId", testFrameworkService.startSuiteAsync(suiteName));
    }

    /**
     * Runs a suite and waits for the outcome, for use as a deployment gate.
     *
     * <p>The response status reflects the result; see the class documentation for the mapping.
     * This request blocks the calling thread until the run finishes or {@code timeout} elapses.
     *
     * @param suiteName     the suite name as registered in {@code @TestSuite#name()}
     * @param timeout       the longest time to wait, as an ISO-8601 duration such as {@code PT5M}
     * @param authorization the {@code Authorization} header, if any
     * @return the run snapshot, with a status code describing the outcome
     * @throws InterruptedException if the request thread is interrupted while waiting
     */
    @PostMapping("/suites/{suiteName}/gate")
    public ResponseEntity<SuiteRunStatus> gateSuite(
            @PathVariable String suiteName,
            @RequestParam(defaultValue = DEFAULT_GATE_TIMEOUT) Duration timeout,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization)
            throws InterruptedException {
        authorize(authorization);
        if (timeout.isNegative() || timeout.isZero()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "timeout must be positive");
        }

        String runId = testFrameworkService.startSuiteAsync(suiteName);
        SuiteRunStatus status = testFrameworkService.awaitRun(runId, timeout);
        if (status.getStatus().isTerminal()) {
            return ResponseEntity.status(gateStatusFor(status)).body(status);
        }

        // The run may finish between the timeout and the cancel; report whatever it ended as.
        SuiteRunStatus cancelled = testFrameworkService.cancelRun(runId);
        HttpStatus httpStatus = cancelled.getStatus() == SuiteRunStatus.Status.CANCELLED
                ? HttpStatus.GATEWAY_TIMEOUT
                : gateStatusFor(cancelled);
        return ResponseEntity.status(httpStatus).body(cancelled);
    }

    /**
     * Starts a single test within a suite asynchronously.
     *
     * @param suiteName     the suite containing the test
     * @param testName      the test name as registered in {@code @Test#name()}
     * @param authorization the {@code Authorization} header, if any
     * @return map containing {@code runId}
     */
    @PostMapping("/suites/{suiteName}/tests/{testName}/run")
    public Map<String, String> runSingleTest(@PathVariable String suiteName,
                                              @PathVariable String testName,
                                              @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authorize(authorization);
        return Map.of("runId", testFrameworkService.startSingleTestAsync(suiteName, testName));
    }

    /**
     * Returns the current status snapshot for the given run.
     *
     * @param runId         the run identifier returned by a start-run endpoint
     * @param authorization the {@code Authorization} header, if any
     * @return the live {@link SuiteRunStatus} snapshot
     */
    @GetMapping("/runs/{runId}/status")
    public SuiteRunStatus getRunStatus(@PathVariable String runId,
                                       @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authorize(authorization);
        return testFrameworkService.getRunStatus(runId);
    }

    /**
     * Cancels a running or pending run.
     *
     * @param runId         the run identifier returned by a start-run endpoint
     * @param authorization the {@code Authorization} header, if any
     * @return the terminal {@link SuiteRunStatus} after cancellation
     */
    @PostMapping("/runs/{runId}/cancel")
    public SuiteRunStatus cancelRun(@PathVariable String runId,
                                    @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authorize(authorization);
        return testFrameworkService.cancelRun(runId);
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

    /**
     * Rejects the request unless it carries the configured bearer token. Does nothing when no token
     * is configured. The comparison takes the same time for any token of the same length.
     */
    private void authorize(String authorization) {
        if (apiToken == null) {
            return;
        }
        byte[] expected = (BEARER_PREFIX + apiToken).getBytes(StandardCharsets.UTF_8);
        byte[] provided = authorization == null ? new byte[0] : authorization.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, provided)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid API token");
        }
    }

    private static HttpStatus gateStatusFor(SuiteRunStatus status) {
        return switch (status.getStatus()) {
            case COMPLETED -> status.getFailedCount() == 0 ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_CONTENT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
