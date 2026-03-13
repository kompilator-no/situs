package no.kompilator.sampleapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Java Spring Boot sample app.
 * Verifies auto-discovery, DI injection, and HTTP API behaviour.
 *
 * <p>Tests are ordered so each suite is only started once at a time.
 * Run-triggering tests poll for COMPLETED before the test ends to
 * avoid 409 collisions with subsequent tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.MethodName.class)
class TestFrameworkIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Helper — poll until COMPLETED (max 10 s)
    // -------------------------------------------------------------------------

    private void awaitCompleted(String runId) throws Exception {
        for (int i = 0; i < 100; i++) {
            Thread.sleep(100);
            MvcResult r = mockMvc.perform(get("/api/test-framework/runs/" + runId + "/status"))
                    .andExpect(status().isOk())
                    .andReturn();
            String status = objectMapper.readTree(r.getResponse().getContentAsString())
                    .get("status").asText();
            if ("COMPLETED".equals(status)) return;
        }
        throw new AssertionError("Run " + runId + " did not complete within 10 s");
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void statusEndpointReturnsOk() throws Exception {
        mockMvc.perform(get("/api/test-framework/status"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    void getSuitesReturnsAllFourSuites() throws Exception {
        mockMvc.perform(get("/api/test-framework/suites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'CalculatorTestSuite')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'ParameterizedCalculatorTestSuite')]").exists())
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
    void parameterizedCalculatorSuiteExpandsGeneratedInvocations() throws Exception {
        mockMvc.perform(get("/api/test-framework/suites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.name == 'ParameterizedCalculatorTestSuite')].tests[?(@.name == 'add[1] 1+2=3')]").exists())
                .andExpect(jsonPath(
                        "$[?(@.name == 'ParameterizedCalculatorTestSuite')].tests[?(@.name == 'add[2] 20+22=42')]").exists())
                .andExpect(jsonPath(
                        "$[?(@.name == 'ParameterizedCalculatorTestSuite')].tests[?(@.name == 'multiply[2] 7*6=42')]").exists());
    }

    @Test
    void runSuiteByBodyReturnsRunId() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/test-framework/suites/run/by-name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"LongRunningTestSuite\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").isNotEmpty())
                .andReturn();
        String runId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("runId").asText();
        awaitCompleted(runId);
    }

    @Test
    void runSuiteByNameReturnsRunId() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/test-framework/suites/CalculatorTestSuite/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").isNotEmpty())
                .andReturn();
        String runId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("runId").asText();
        awaitCompleted(runId);
    }

    @Test
    void runRetrySuiteReturnsRunId() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/test-framework/suites/RetryTestSuite/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").isNotEmpty())
                .andReturn();
        String runId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("runId").asText();
        awaitCompleted(runId);
    }

    @Test
    void runParameterizedCalculatorSuiteReturnsPassingInvocations() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/test-framework/suites/ParameterizedCalculatorTestSuite/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").isNotEmpty())
                .andReturn();
        String runId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("runId").asText();
        awaitCompleted(runId);

        mockMvc.perform(get("/api/test-framework/runs/" + runId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedCount").value(5))
                .andExpect(jsonPath("$.passedCount").value(5))
                .andExpect(jsonPath("$.failedCount").value(0));
    }

    @Test
    void unknownSuiteReturns404() throws Exception {
        mockMvc.perform(post("/api/test-framework/suites/NonExistentSuite/run"))
                .andExpect(status().isNotFound());
    }
}
