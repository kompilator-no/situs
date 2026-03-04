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

@SpringBootTest
@AutoConfigureMockMvc
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
    void getSuitesReturnCalculatorSuite() throws Exception {
        mockMvc.perform(get("/api/test-framework/suites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("CalculatorTestSuite"));
    }

    @Test
    void runSuiteByNameReturnsRunId() throws Exception {
        mockMvc.perform(post("/api/test-framework/suites/CalculatorTestSuite/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").isNotEmpty());
    }

    @Test
    void runSuiteByBodyReturnsRunId() throws Exception {
        mockMvc.perform(post("/api/test-framework/suites/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CalculatorTestSuite\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").isNotEmpty());
    }

    @Test
    void unknownSuiteReturns404() throws Exception {
        mockMvc.perform(post("/api/test-framework/suites/NonExistentSuite/run"))
                .andExpect(status().isNotFound());
    }
}
