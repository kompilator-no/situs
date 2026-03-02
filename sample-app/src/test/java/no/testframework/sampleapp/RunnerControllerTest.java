package no.testframework.sampleapp;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
}
