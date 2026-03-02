package no.testframework.sampleapp;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "runner.security.jwt.issuer-uri=https://issuer.example.test",
    "runner.security.jwt.audience=runner-service",
    "runner.history-ttl=PT1S",
    "runner.max-run-records=1"
})
class RunRetentionControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void summaryExposesRetentionMetrics() throws Exception {
        startRun();
        Thread.sleep(200);

        mvc.perform(get("/api/runs/summary").with(token("runner:read")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expiredDeleted").isNumber())
            .andExpect(jsonPath("$.retainedCount").isNumber());
    }

    @Test
    void completedHistoryIsCappedByMaxRunRecords() throws Exception {
        startRun();
        startRun();
        startRun();
        Thread.sleep(400);

        mvc.perform(get("/api/runs")
                .with(token("runner:read"))
                .queryParam("state", "COMPLETED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(lessThanOrEqualTo(1)));

        mvc.perform(get("/api/runs/summary").with(token("runner:read")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.retainedCount").value(lessThanOrEqualTo(1)));
    }

    private void startRun() throws Exception {
        mvc.perform(post("/api/runs")
                .with(token("runner:execute"))
                .contentType("application/json")
                .content("""
                    {
                      \"testId\": \"smoke-test\"
                    }
                    """))
            .andExpect(status().isAccepted());
    }

    private RequestPostProcessor token(String... authorities) {
        GrantedAuthority[] granted = Arrays.stream(authorities)
            .map(SimpleGrantedAuthority::new)
            .toArray(GrantedAuthority[]::new);
        return jwt().authorities(granted);
    }
}
