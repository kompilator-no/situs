package no.testframework.javalibrary.fixtures;

import no.testframework.javalibrary.spring.model.SuiteRunStatus;
import no.testframework.javalibrary.spring.TestFrameworkService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared polling helpers used by async test assertions.
 */
public final class AsyncTestHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_POLLS       = 50;
    private static final long POLL_INTERVAL  = 100;

    private AsyncTestHelper() {}

    /**
     * Polls {@link TestFrameworkService#getRunStatus(String)} every 100 ms until
     * the run reaches {@code COMPLETED} or the timeout (5 s) is exceeded.
     */
    public static SuiteRunStatus awaitCompleted(TestFrameworkService service, String runId)
            throws InterruptedException {
        for (int i = 0; i < MAX_POLLS; i++) {
            Thread.sleep(POLL_INTERVAL);
            SuiteRunStatus s = service.getRunStatus(runId);
            if (s.getStatus() == SuiteRunStatus.Status.COMPLETED) return s;
        }
        throw new AssertionError("Run " + runId + " did not reach COMPLETED within 5 s");
    }

    /**
     * Polls {@code GET /api/test-framework/runs/{runId}/status} via MockMvc every 100 ms
     * until the run reaches {@code COMPLETED} or the timeout (5 s) is exceeded.
     *
     * @return the final {@link JsonNode} response body
     */
    public static JsonNode awaitCompleted(MockMvc mockMvc, String runId) throws Exception {
        for (int i = 0; i < MAX_POLLS; i++) {
            Thread.sleep(POLL_INTERVAL);
            MvcResult r = mockMvc.perform(get("/api/test-framework/runs/" + runId + "/status"))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode node = MAPPER.readTree(r.getResponse().getContentAsString());
            if (SuiteRunStatus.Status.COMPLETED.name().equals(node.get("status").asText())) {
                return node;
            }
        }
        throw new AssertionError("Run " + runId + " did not reach COMPLETED within 5 s");
    }
}
