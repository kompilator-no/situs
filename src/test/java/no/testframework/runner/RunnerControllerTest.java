package no.testframework.runner;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
