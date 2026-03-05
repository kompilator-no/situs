package no.testframework.sampleapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the test framework HTTP API in the sample application.
 *
 * <p>Starts the full Spring Boot context (including auto-configuration) and exercises
 * the REST endpoints provided by the library's
 * {@code TestFrameworkController}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>The framework activates automatically via Spring Boot auto-configuration.</li>
 *   <li>{@link no.testframework.sampleapp.tests.CalculatorTestSuite},
 *       {@link no.testframework.sampleapp.tests.LongRunningTestSuite}, and
 *       {@link no.testframework.sampleapp.tests.RetryTestSuite} are all discovered
 *       automatically via classpath scanning.</li>
 *   <li>{@code CalculatorTestSuite} is resolved from the Spring context and receives
 *       its {@link no.testframework.sampleapp.tests.Calculator} dependency via injection.</li>
 *   <li>Suite runs can be started by name (path variable) or by JSON body.</li>
 *   <li>Unknown suite names return {@code 404 Not Found}.</li>
 *   <li>The {@code RetryTestSuite} is discovered and can be started — demonstrating
 *       the retries feature end-to-end.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class TestFrameworkIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Verifies that {@code GET /api/test-framework/status} returns {@code 200 OK}
     * with body {@code "OK"}.
     */
    @Test
    void statusEndpointReturnsOk() throws Exception {
        mockMvc.perform(get("/api/test-framework/status"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    /**
     * Verifies that all three suites are discovered automatically from the classpath.
     */
    @Test
    void getSuitesReturnsAllThreeSuites() throws Exception {
        mockMvc.perform(get("/api/test-framework/suites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'CalculatorTestSuite')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'LongRunningTestSuite')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'RetryTestSuite')]").exists());
    }

    /**
     * Verifies that {@code CalculatorTestSuite} exposes all five test cases
     * and that its {@code description} field is set.
     */
    @Test
    void calculatorSuiteHasExpectedTestCasesAndDescription() throws Exception {
        mockMvc.perform(get("/api/test-framework/suites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'CalculatorTestSuite')].description")
                        .value("Tests for the Calculator class"))
                .andExpect(jsonPath("$[?(@.name == 'CalculatorTestSuite')].tests[?(@.name == 'addition')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'CalculatorTestSuite')].tests[?(@.name == 'subtraction')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'CalculatorTestSuite')].tests[?(@.name == 'multiplication')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'CalculatorTestSuite')].tests[?(@.name == 'division')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'CalculatorTestSuite')].tests[?(@.name == 'divisionByZero')]").exists());
    }

    /**
     * Verifies that {@code RetryTestSuite} is discovered and exposes all three test cases.
     */
    @Test
    void retrySuiteHasExpectedTestCases() throws Exception {
        mockMvc.perform(get("/api/test-framework/suites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'RetryTestSuite')].tests[?(@.name == 'flakyExternalCheck')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'RetryTestSuite')].tests[?(@.name == 'alwaysFailingCheck')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'RetryTestSuite')].tests[?(@.name == 'stableCheck')]").exists());
    }

    /**
     * Verifies that a suite run can be started by suite name in the path and
     * that the response contains a non-empty {@code runId}.
     */
    @Test
    void runSuiteByNameReturnsRunId() throws Exception {
        mockMvc.perform(post("/api/test-framework/suites/CalculatorTestSuite/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").isNotEmpty());
    }

    /**
     * Verifies that a suite run can be started by passing the suite name in
     * a JSON request body and that the response contains a non-empty {@code runId}.
     */
    @Test
    void runSuiteByBodyReturnsRunId() throws Exception {
        mockMvc.perform(post("/api/test-framework/suites/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CalculatorTestSuite\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").isNotEmpty());
    }

    /**
     * Verifies that the retry suite run can be started, returning a valid {@code runId}.
     */
    @Test
    void runRetrySuiteReturnsRunId() throws Exception {
        mockMvc.perform(post("/api/test-framework/suites/RetryTestSuite/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").isNotEmpty());
    }

    /**
     * Verifies that attempting to run a suite that does not exist returns
     * {@code 404 Not Found}.
     */
    @Test
    void unknownSuiteReturns404() throws Exception {
        mockMvc.perform(post("/api/test-framework/suites/NonExistentSuite/run"))
                .andExpect(status().isNotFound());
    }
}
