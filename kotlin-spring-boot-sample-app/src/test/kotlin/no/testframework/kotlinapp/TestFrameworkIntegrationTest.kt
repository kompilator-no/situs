package no.testframework.kotlinapp

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

/**
 * Integration tests for the Kotlin Spring Boot sample app.
 *
 * Starts the full Spring Boot context and exercises the REST endpoints provided
 * by the library's `TestFrameworkController`.
 *
 * Verifies:
 * - The framework activates automatically via Spring Boot auto-configuration.
 * - All three Kotlin suites ([no.testframework.kotlinapp.tests.CalculatorTestSuite],
 *   [no.testframework.kotlinapp.tests.LongRunningTestSuite],
 *   [no.testframework.kotlinapp.tests.RetryTestSuite]) are discovered automatically.
 * - `CalculatorTestSuite` receives its [no.testframework.kotlinapp.tests.Calculator]
 *   dependency via constructor injection.
 * - Suite runs can be started by name (path variable) or by JSON body.
 * - Unknown suite names return `404 Not Found`.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TestFrameworkIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun statusEndpointReturnsOk() {
        mockMvc.perform(get("/api/test-framework/status"))
            .andExpect(status().isOk)
            .andExpect(content().string("OK"))
    }

    @Test
    fun getSuitesReturnsAllThreeSuites() {
        mockMvc.perform(get("/api/test-framework/suites"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.name == 'CalculatorTestSuite')]").exists())
            .andExpect(jsonPath("$[?(@.name == 'LongRunningTestSuite')]").exists())
            .andExpect(jsonPath("$[?(@.name == 'RetryTestSuite')]").exists())
    }

    @Test
    fun calculatorSuiteHasExpectedTestCases() {
        mockMvc.perform(get("/api/test-framework/suites"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.name == 'CalculatorTestSuite')].description")
                .value("Tests for the Calculator class"))
            .andExpect(jsonPath("$[?(@.name == 'CalculatorTestSuite')].tests[?(@.name == 'addition')]").exists())
            .andExpect(jsonPath("$[?(@.name == 'CalculatorTestSuite')].tests[?(@.name == 'subtraction')]").exists())
            .andExpect(jsonPath("$[?(@.name == 'CalculatorTestSuite')].tests[?(@.name == 'multiplication')]").exists())
            .andExpect(jsonPath("$[?(@.name == 'CalculatorTestSuite')].tests[?(@.name == 'division')]").exists())
            .andExpect(jsonPath("$[?(@.name == 'CalculatorTestSuite')].tests[?(@.name == 'divisionByZero')]").exists())
    }

    @Test
    fun retrySuiteHasExpectedTestCases() {
        mockMvc.perform(get("/api/test-framework/suites"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.name == 'RetryTestSuite')].tests[?(@.name == 'flakyExternalCheck')]").exists())
            .andExpect(jsonPath("$[?(@.name == 'RetryTestSuite')].tests[?(@.name == 'alwaysFailingCheck')]").exists())
            .andExpect(jsonPath("$[?(@.name == 'RetryTestSuite')].tests[?(@.name == 'stableCheck')]").exists())
    }

    @Test
    fun runSuiteByNameReturnsRunId() {
        mockMvc.perform(post("/api/test-framework/suites/CalculatorTestSuite/run"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.runId").isNotEmpty)
    }

    @Test
    fun runSuiteByBodyReturnsRunId() {
        mockMvc.perform(
            post("/api/test-framework/suites/run")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"CalculatorTestSuite"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.runId").isNotEmpty)
    }

    @Test
    fun runRetrySuiteReturnsRunId() {
        mockMvc.perform(post("/api/test-framework/suites/RetryTestSuite/run"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.runId").isNotEmpty)
    }

    @Test
    fun unknownSuiteReturns404() {
        mockMvc.perform(post("/api/test-framework/suites/NonExistentSuite/run"))
            .andExpect(status().isNotFound)
    }
}
