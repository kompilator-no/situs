package no.testframework.sampleapp;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
    "runner.security.jwt.issuer-uri=https://issuer.example.test",
    "runner.security.jwt.audience=runner-service"
})
class RunnerControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldRejectUnauthorizedRequests() throws Exception {
        mvc.perform(get("/api/tests"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowReadOnlyTokenToListTestsAndRuns() throws Exception {
        mvc.perform(get("/api/tests").with(jwt().jwt(jwt -> jwt.claim("scope", "runner:read"))))
            .andExpect(status().isOk());

        mvc.perform(get("/api/runs").with(jwt().jwt(jwt -> jwt.claim("scope", "runner:read"))))
            .andExpect(status().isOk());
    }

    @Test
    void shouldDenyReadOnlyTokenForRunStartAndCancel() throws Exception {
        mvc.perform(post("/api/runs")
                .with(jwt().jwt(jwt -> jwt.claim("scope", "runner:read")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "testId": "smoke-test"
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowExecuteTokenToStartAndCancelRuns() throws Exception {
        MvcResult startResult = mvc.perform(post("/api/runs")
                .with(jwt().jwt(jwt -> jwt.claim("scope", "runner:execute runner:cancel")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "testId": "smoke-test",
                      "retries": 1,
                      "timeout": "PT5S"
                    }
                    """))
            .andExpect(status().isAccepted())
            .andReturn();

        JsonNode body = objectMapper.readTree(startResult.getResponse().getContentAsString());
        String runId = body.get("runId").asText();

        mvc.perform(delete("/api/runs/{runId}", runId)
                .with(jwt().jwt(jwt -> jwt.claim("scope", "runner:cancel"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cancelled").isBoolean());
    }

    @Test
    void shouldSupportRunPaginationParameters() throws Exception {
        mvc.perform(post("/api/runs")
                .with(jwt().jwt(jwt -> jwt.claim("scope", "runner:execute")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "testId": "smoke-test"
                    }
                    """))
            .andExpect(status().isAccepted());

        mvc.perform(get("/api/runs")
                .with(jwt().jwt(jwt -> jwt.claim("scope", "runner:read")))
                .queryParam("testId", "smoke-test")
                .queryParam("limit", "1")
                .queryParam("offset", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(lessThanOrEqualTo(1)));
    }

    @Test
    void shouldFilterRunsAndExposeSummary() throws Exception {
        mvc.perform(post("/api/runs")
                .with(jwt().jwt(jwt -> jwt.claim("scope", "runner:execute")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "testId": "smoke-test"
                    }
                    """))
            .andExpect(status().isAccepted());

        mvc.perform(get("/api/runs")
                .with(jwt().jwt(jwt -> jwt.claim("scope", "runner:read")))
                .queryParam("testId", "smoke-test"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/runs/summary")
                .with(jwt().jwt(jwt -> jwt.claim("scope", "runner:read"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").isNumber())
            .andExpect(jsonPath("$.queued").isNumber())
            .andExpect(jsonPath("$.running").isNumber())
            .andExpect(jsonPath("$.completed").isNumber());
    }

    @Test
    void shouldPropagateCorrelationAndTraceAndEmitStructuredLifecycleLogs(CapturedOutput output) throws Exception {
        String correlationId = "corr-e2e-123";
        MvcResult runStartResult = mvc.perform(post("/api/runs")
                .header("X-Correlation-Id", correlationId)
                .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
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
                      "timeout": "PT2S"
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

        JsonNode startPayload = objectMapper.readTree(runStartResult.getResponse().getContentAsString());
        String runId = startPayload.get("runId").asText();

        Thread.sleep(200);

        mvc.perform(get("/api/runs/{runId}", runId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.correlationId").value(correlationId))
            .andExpect(jsonPath("$.traceId").isString())
            .andExpect(jsonPath("$.state").value("COMPLETED"));

        String logs = output.getOut();
        assertTrue(logs.contains("\"runId\":\"" + runId + "\""));
        assertTrue(logs.contains("\"testId\":\"smoke-test\""));
        assertTrue(logs.contains("\"state\":\"QUEUED\""));
        assertTrue(logs.contains("\"state\":\"RUNNING\""));
        assertTrue(logs.contains("\"state\":\"COMPLETED\""));
        assertTrue(logs.contains("\"attempt\":"));
        assertTrue(logs.contains("\"correlationId\":\"" + correlationId + "\""));
        String firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.runId");
        String secondId = JsonPath.read(second.getResponse().getContentAsString(), "$.runId");
        org.junit.jupiter.api.Assertions.assertNotEquals(firstId, secondId);
    }
}
