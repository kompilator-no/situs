package no.testframework.kotlinapp

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

/**
 * Integration tests for the Kotlin Spring Boot sample app.
 *
 * Tests are ordered alphabetically so run-triggering tests complete before
 * the next test starts — avoiding 409 Conflict responses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.MethodName::class)
class TestFrameworkIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val objectMapper = ObjectMapper()

    // -------------------------------------------------------------------------
    // Helper — poll until COMPLETED (max 10 s)
    // -------------------------------------------------------------------------

    private fun awaitCompleted(runId: String) {
        repeat(100) {
            Thread.sleep(100)
            val r = mockMvc.perform(get("/api/test-framework/runs/$runId/status"))
                .andExpect(status().isOk)
                .andReturn()
            val status = objectMapper.readTree(r.response.contentAsString).get("status").asText()
            if (status == "COMPLETED") return
        }
        throw AssertionError("Run $runId did not complete within 10 s")
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

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
    fun runSuiteByBodyReturnsRunId() {
        val result = mockMvc.perform(
            post("/api/test-framework/suites/run/by-name")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"LongRunningTestSuite"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.runId").isNotEmpty)
            .andReturn()
        val runId = objectMapper.readTree(result.response.contentAsString).get("runId").asText()
        awaitCompleted(runId)
    }

    @Test
    fun runSuiteByNameReturnsRunId() {
        val result = mockMvc.perform(post("/api/test-framework/suites/CalculatorTestSuite/run"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.runId").isNotEmpty)
            .andReturn()
        val runId = objectMapper.readTree(result.response.contentAsString).get("runId").asText()
        awaitCompleted(runId)
    }

    @Test
    fun runRetrySuiteReturnsRunId() {
        val result = mockMvc.perform(post("/api/test-framework/suites/RetryTestSuite/run"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.runId").isNotEmpty)
            .andReturn()
        val runId = objectMapper.readTree(result.response.contentAsString).get("runId").asText()
        awaitCompleted(runId)
    }

    @Test
    fun unknownSuiteReturns404() {
        mockMvc.perform(post("/api/test-framework/suites/NonExistentSuite/run"))
            .andExpect(status().isNotFound)
    }
}
