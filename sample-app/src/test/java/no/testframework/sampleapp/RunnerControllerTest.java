package no.testframework.sampleapp;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class RunnerControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void shouldListDiscoveredTests() throws Exception {
        mvc.perform(get("/api/tests"))
            .andExpect(status().isOk());
    }

    @Test
    void shouldAcceptRunStart() throws Exception {
        mvc.perform(post("/api/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "testId": "smoke-test",
                      "retries": 1,
                      "timeout": "PT5S"
                    }
                    """))
            .andExpect(status().isAccepted());
    }


    @Test
    void shouldSupportRunPaginationParameters() throws Exception {
        mvc.perform(post("/api/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "testId": "smoke-test"
                    }
                    """))
            .andExpect(status().isAccepted());

        mvc.perform(get("/api/runs")
                .queryParam("testId", "smoke-test")
                .queryParam("limit", "1")
                .queryParam("offset", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(lessThanOrEqualTo(1)));
    }

    @Test
    void shouldFilterRunsAndExposeSummary() throws Exception {
        mvc.perform(post("/api/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "testId": "smoke-test"
                    }
                    """))
            .andExpect(status().isAccepted());

        mvc.perform(get("/api/runs").queryParam("testId", "smoke-test"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/runs/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").isNumber())
            .andExpect(jsonPath("$.queued").isNumber())
            .andExpect(jsonPath("$.running").isNumber())
            .andExpect(jsonPath("$.completed").isNumber());
    }

    @Test
    void sameIdempotencyKeyAndPayloadReturnsSameRun() throws Exception {
        String payload = """
            {
              "testId": "smoke-test",
              "retries": 1,
              "timeout": "PT5S",
              "context": {
                "delayMs": 15
              }
            }
            """;

        MvcResult first = mvc.perform(post("/api/runs")
                .header("Idempotency-Key", "idem-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isAccepted())
            .andReturn();

        MvcResult second = mvc.perform(post("/api/runs")
                .header("Idempotency-Key", "idem-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isAccepted())
            .andReturn();

        String firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.runId");
        String secondId = JsonPath.read(second.getResponse().getContentAsString(), "$.runId");
        assertEquals(firstId, secondId);
    }

    @Test
    void sameIdempotencyKeyAndDifferentPayloadReturnsConflict() throws Exception {
        mvc.perform(post("/api/runs")
                .header("Idempotency-Key", "idem-key-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "testId": "smoke-test",
                      "retries": 0,
                      "timeout": "PT5S"
                    }
                    """))
            .andExpect(status().isAccepted());

        mvc.perform(post("/api/runs")
                .header("Idempotency-Key", "idem-key-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "testId": "smoke-test",
                      "retries": 2,
                      "timeout": "PT5S"
                    }
                    """))
            .andExpect(status().isConflict());
    }


    @Test
    void sameIdempotencyKeyWithEquivalentContextOrderReturnsSameRun() throws Exception {
        MvcResult first = mvc.perform(post("/api/runs")
                .header("Idempotency-Key", "idem-key-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "testId": "smoke-test",
                      "context": {
                        "a": 1,
                        "b": 2
                      }
                    }
                    """))
            .andExpect(status().isAccepted())
            .andReturn();

        MvcResult second = mvc.perform(post("/api/runs")
                .header("Idempotency-Key", "idem-key-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "testId": "smoke-test",
                      "context": {
                        "b": 2,
                        "a": 1
                      }
                    }
                    """))
            .andExpect(status().isAccepted())
            .andReturn();

        String firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.runId");
        String secondId = JsonPath.read(second.getResponse().getContentAsString(), "$.runId");
        assertEquals(firstId, secondId);
    }

    @Test
    void missingIdempotencyKeyKeepsExistingBehavior() throws Exception {
        MvcResult first = mvc.perform(post("/api/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "testId": "smoke-test"
                    }
                    """))
            .andExpect(status().isAccepted())
            .andReturn();

        MvcResult second = mvc.perform(post("/api/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "testId": "smoke-test"
                    }
                    """))
            .andExpect(status().isAccepted())
            .andReturn();

        String firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.runId");
        String secondId = JsonPath.read(second.getResponse().getContentAsString(), "$.runId");
        org.junit.jupiter.api.Assertions.assertNotEquals(firstId, secondId);
    }
}
