package no.testframework.sampleapp;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
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
}
