package no.testframework.sampleapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Java Spring Boot sample app.
 * Verifies auto-discovery, DI injection, and HTTP API behaviour.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TestFrameworkIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void statusEndpointReturnsOk() throws Exception {
        mockMvc.perform(get("/api/test-framework/status"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    void getSuitesReturnsAllThreeSuites() throws Exception {
        mockMvc.perform(get("/api/test-framework/suites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'CalculatorTestSuite')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'LongRunningTestSuite')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'RetryTestSuite')]").exists());
    }

    @Test
    void calculatorSuiteHasExpectedTestCases() throws Exception {
        mockMvc.perform(get("/api/test-framework/suites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'CalculatorTestSuite')].tests[?(@.name == 'addition')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'CalculatorTestSuite')].tests[?(@.name == 'subtraction')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'CalculatorTestSuite')].tests[?(@.name == 'divisionByZero')]").exists());
    }

    @Test
    void retrySuiteHasExpectedTestCases() throws Exception {
        mockMvc.perform(get("/api/test-framework/suites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'RetryTestSuite')].tests[?(@.name == 'flakyExternalCheck')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'RetryTestSuite')].tests[?(@.name == 'alwaysFailingCheck')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'RetryTestSuite')].tests[?(@.name == 'stableCheck')]").exists());
    }

    @Test
    void runSuiteByNameReturnsRunId() throws Exception {
        mockMvc.perform(post("/api/test-framework/suites/CalculatorTestSuite/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").isNotEmpty());
    }

    @Test
    void runSuiteByBodyReturnsRunId() throws Exception {
        mockMvc.perform(post("/api/test-framework/suites/run/by-name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CalculatorTestSuite\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").isNotEmpty());
    }

    @Test
    void runRetrySuiteReturnsRunId() throws Exception {
        mockMvc.perform(post("/api/test-framework/suites/RetryTestSuite/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").isNotEmpty());
    }

    @Test
    void unknownSuiteReturns404() throws Exception {
        mockMvc.perform(post("/api/test-framework/suites/NonExistentSuite/run"))
                .andExpect(status().isNotFound());
    }
}
