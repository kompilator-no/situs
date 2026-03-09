package no.certusdev.testframework.javalibrary.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.certusdev.testframework.javalibrary.model.TestSuite;
import no.certusdev.testframework.javalibrary.spring.TestFrameworkController;
import no.certusdev.testframework.javalibrary.model.SuiteRunStatus;
import no.certusdev.testframework.javalibrary.service.TestFrameworkService;
import no.certusdev.testframework.javalibrary.fixtures.AsyncTestHelper;
import no.certusdev.testframework.javalibrary.fixtures.TestSuiteFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


class TestFrameworkControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        TestSuiteFixtures.RetryControllerSuite.callCount.set(0);
        TestFrameworkService service = new TestFrameworkService(Set.of(
                TestSuiteFixtures.ControllerSuite.class,
                TestSuiteFixtures.SlowSuite.class,
                TestSuiteFixtures.LongRunningSuite.class,
                TestSuiteFixtures.RetryControllerSuite.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new TestFrameworkController(service)).build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Starts an async run via POST and returns the runId. */
    private String startRun(String path) throws Exception {
        MvcResult r = mockMvc.perform(post(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId", notNullValue()))
                .andReturn();

        return objectMapper.readTree(r.getResponse().getContentAsString()).get("runId").asText();
    }

    /** Delegates to the shared MockMvc overload of AsyncTestHelper. */
    private JsonNode awaitCompleted(String runId) throws Exception {
        return AsyncTestHelper.awaitCompleted(mockMvc, runId);
    }

    // -------------------------------------------------------------------------
    // GET /status
    // -------------------------------------------------------------------------

    @Test
    void statusEndpointReturnsOk() throws Exception {
        mockMvc.perform(get("/api/test-framework/status"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    // -------------------------------------------------------------------------
    // GET /suites
    // -------------------------------------------------------------------------

    @Test
    void getSuitesReturnsAllSuites() throws Exception {
        mockMvc.perform(get("/api/test-framework/suites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[?(@.name == 'Controller Suite')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.name == 'Slow Suite')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.name == 'Long Running Suite')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.name == 'Retry Controller Suite')]", hasSize(1)));
    }

    @Test
    void getSuitesIncludesTestCases() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/test-framework/suites"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode suites = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode controllerSuite = null;
        for (JsonNode suite : suites) {
            if ("Controller Suite".equals(suite.get("name").asText())) {
                controllerSuite = suite;
                break;
            }
        }
        assertThat(controllerSuite).isNotNull();
        JsonNode tests = controllerSuite.get("tests");
        assertThat(tests).hasSize(3);

        List<String> testNames = new ArrayList<>();
        tests.forEach(t -> testNames.add(t.get("name").asText()));
        assertThat(testNames).containsExactlyInAnyOrder("passing", "failing", "timeout");
    }

    // -------------------------------------------------------------------------
    // POST /suites/run/by-name  (run by body)
    // -------------------------------------------------------------------------

    @Test
    void runSuiteByBodyReturnsRunId() throws Exception {
        mockMvc.perform(post("/api/test-framework/suites/run/by-name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Controller Suite\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId", notNullValue()));
    }

    @Test
    void runSuiteByBodyCompletesWithExpectedCounts() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/test-framework/suites/run/by-name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Controller Suite\"}"))
                .andReturn();
        String runId = objectMapper.readTree(r.getResponse().getContentAsString()).get("runId").asText();

        JsonNode result = awaitCompleted(runId);
        assertThat(result.get("passedCount").asInt()).isEqualTo(1);
        assertThat(result.get("failedCount").asInt()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // POST /suites/{suiteName}/run  (run by path)
    // -------------------------------------------------------------------------

    @Test
    void runSuiteByPathReturnsRunId() throws Exception {
        mockMvc.perform(post("/api/test-framework/suites/Controller Suite/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId", notNullValue()));
    }

    @Test
    void runSuiteByPathCompletesWithExpectedCounts() throws Exception {
        String runId = startRun("/api/test-framework/suites/Controller Suite/run");
        JsonNode result = awaitCompleted(runId);

        assertThat(result.get("suiteName").asText()).isEqualTo("Controller Suite");
        assertThat(result.get("passedCount").asInt()).isEqualTo(1);
        assertThat(result.get("failedCount").asInt()).isEqualTo(2);
        assertThat(result.get("completedResults")).hasSize(3);
    }

    // -------------------------------------------------------------------------
    // POST /suites/{suiteName}/tests/{testName}/run  (single test)
    // -------------------------------------------------------------------------

    @Test
    void runSinglePassingTestReturnsRunId() throws Exception {
        mockMvc.perform(post("/api/test-framework/suites/Controller Suite/tests/passing/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId", notNullValue()));
    }

    @Test
    void runSinglePassingTestCompletesAsPassed() throws Exception {
        String runId = startRun("/api/test-framework/suites/Controller Suite/tests/passing/run");
        JsonNode result = awaitCompleted(runId);

        assertThat(result.get("completedResults")).hasSize(1);
        assertThat(result.get("completedResults").get(0).get("name").asText()).isEqualTo("passing");
        assertThat(result.get("completedResults").get(0).get("passed").asBoolean()).isTrue();
    }

    @Test
    void runSingleFailingTestCompletesAsFailed() throws Exception {
        String runId = startRun("/api/test-framework/suites/Controller Suite/tests/failing/run");
        JsonNode result = awaitCompleted(runId);

        assertThat(result.get("completedResults").get(0).get("passed").asBoolean()).isFalse();
        assertThat(result.get("completedResults").get(0).get("errorMessage").asText()).isEqualTo("boom");
    }

    @Test
    void runSingleTimedOutTestCompletesAsFailed() throws Exception {
        String runId = startRun("/api/test-framework/suites/Controller Suite/tests/timeout/run");
        JsonNode result = awaitCompleted(runId);

        assertThat(result.get("completedResults").get(0).get("passed").asBoolean()).isFalse();
        assertThat(result.get("completedResults").get(0).get("errorMessage").asText()).contains("timed out");
    }

    @Test
    void startingSameSuiteWhileItIsRunningReturnsConflict() throws Exception {
        startRun("/api/test-framework/suites/Long Running Suite/run");

        mockMvc.perform(post("/api/test-framework/suites/Long Running Suite/run"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("already running")));
    }

    @Test
    void startingSameSingleTestWhileItIsRunningReturnsConflict() throws Exception {
        startRun("/api/test-framework/suites/Slow Suite/tests/slowTest/run");

        mockMvc.perform(post("/api/test-framework/suites/Slow Suite/tests/slowTest/run"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("already running")));
    }

    // -------------------------------------------------------------------------
    // GET /runs/{runId}/status — live status polling
    // -------------------------------------------------------------------------

    @Test
    void statusIsPendingOrRunningShortlyAfterStart() throws Exception {
        String runId = startRun("/api/test-framework/suites/Slow Suite/run");

        MvcResult statusResult = mockMvc.perform(get("/api/test-framework/runs/" + runId + "/status"))
                .andExpect(status().isOk())
                .andReturn();

        SuiteRunStatus.Status status = SuiteRunStatus.Status.valueOf(
                objectMapper.readTree(statusResult.getResponse().getContentAsString())
                        .get("status").asText());
        assertThat(status).isIn(SuiteRunStatus.Status.PENDING, SuiteRunStatus.Status.RUNNING);
    }

    @Test
    void runningStatusCanContainPartialResults() throws Exception {
        String runId = startRun("/api/test-framework/suites/Slow Suite/run");

        JsonNode partial = null;
        for (int i = 0; i < 20; i++) {
            Thread.sleep(50);
            MvcResult poll = mockMvc.perform(get("/api/test-framework/runs/" + runId + "/status"))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode node = objectMapper.readTree(poll.getResponse().getContentAsString());
            if ("RUNNING".equals(node.get("status").asText()) && node.get("completedResults").size() == 1) {
                partial = node;
                break;
            }
        }

        assertThat(partial).isNotNull();
        assertThat(partial.get("completedCount").asInt()).isEqualTo(1);
        assertThat(partial.get("totalCount").asInt()).isEqualTo(2);
        assertThat(partial.get("passedCount").asInt()).isEqualTo(1);
        assertThat(partial.get("failedCount").asInt()).isEqualTo(0);
        assertThat(partial.get("completedResults").get(0).get("name").asText()).isEqualTo("fastTest");
    }

    @Test
    void statusBecomesCompletedAfterSuiteFinishes() throws Exception {
        String runId = startRun("/api/test-framework/suites/Slow Suite/run");
        JsonNode result = awaitCompleted(runId);

        assertThat(result.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(result.get("suiteName").asText()).isEqualTo("Slow Suite");
    }

    @Test
    void completedStatusContainsAllTestResults() throws Exception {
        String runId = startRun("/api/test-framework/suites/Slow Suite/run");
        JsonNode result = awaitCompleted(runId);

        assertThat(result.get("completedResults")).hasSize(2);
        assertThat(result.get("passedCount").asInt()).isEqualTo(2);
        assertThat(result.get("failedCount").asInt()).isEqualTo(0);
    }

    @Test
    void unknownRunIdReturns404() throws Exception {
        mockMvc.perform(get("/api/test-framework/runs/does-not-exist/status"))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // Long-running test — observe RUNNING status while test executes
    // -------------------------------------------------------------------------

    @Test
    void canObserveRunningStatusWhileLongTestIsExecuting() throws Exception {
        String runId = startRun("/api/test-framework/suites/Long Running Suite/run");

        List<SuiteRunStatus.Status> observedStatuses = new ArrayList<>();
        JsonNode finalResult = null;

        for (int i = 0; i < 50; i++) {
            Thread.sleep(200);
            MvcResult poll = mockMvc.perform(get("/api/test-framework/runs/" + runId + "/status"))
                    .andExpect(status().isOk()).andReturn();
            JsonNode node = objectMapper.readTree(poll.getResponse().getContentAsString());
            SuiteRunStatus.Status current = SuiteRunStatus.Status.valueOf(node.get("status").asText());
            observedStatuses.add(current);
            if (current == SuiteRunStatus.Status.COMPLETED || current == SuiteRunStatus.Status.FAILED) {
                finalResult = node;
                break;
            }
        }

        assertThat(observedStatuses)
                .as("RUNNING should be observed at least once during execution")
                .contains(SuiteRunStatus.Status.RUNNING);
        assertThat(observedStatuses).last().isEqualTo(SuiteRunStatus.Status.COMPLETED);
        assertThat(finalResult).isNotNull();
        assertThat(finalResult.get("suiteName").asText()).isEqualTo("Long Running Suite");
        assertThat(finalResult.get("completedResults")).hasSize(2);
        assertThat(finalResult.get("completedCount").asInt()).isEqualTo(2);
        assertThat(finalResult.get("totalCount").asInt()).isEqualTo(2);
        assertThat(finalResult.get("passedCount").asInt()).isEqualTo(2);
        assertThat(finalResult.get("failedCount").asInt()).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Retries — attempts field in JSON response
    // -------------------------------------------------------------------------

    @Test
    void retryPassSuiteResultContainsAttemptsField() throws Exception {
        TestSuiteFixtures.RetryControllerSuite.callCount.set(0);
        String runId = startRun("/api/test-framework/suites/Retry Controller Suite/run");
        JsonNode result = awaitCompleted(runId);

        assertThat(result.get("passedCount").asInt()).isEqualTo(1);
        assertThat(result.get("failedCount").asInt()).isEqualTo(0);
        assertThat(result.get("completedResults").get(0).get("attempts").asInt())
                .as("Should have taken 3 attempts (retries = 2)")
                .isEqualTo(3);
    }

    @Test
    void retryPassSuiteTestIsPassedInResponse() throws Exception {
        TestSuiteFixtures.RetryControllerSuite.callCount.set(0);
        String runId = startRun("/api/test-framework/suites/Retry Controller Suite/run");
        JsonNode result = awaitCompleted(runId);

        assertThat(result.get("completedResults").get(0).get("passed").asBoolean()).isTrue();
        assertThat(result.get("completedResults").get(0).get("errorMessage").isNull()).isTrue();
    }

    @Test
    void getSuitesIncludesRetryControllerSuite() throws Exception {
        mockMvc.perform(get("/api/test-framework/suites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Retry Controller Suite')]").exists());
    }
}
