package no.kompilator.situs.api;

import no.kompilator.situs.fixtures.TestSuiteFixtures;
import no.kompilator.situs.service.TestFrameworkService;
import no.kompilator.situs.spring.TestFrameworkController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TestFrameworkControllerGateAndAuthTest {

    private static final String TOKEN = "s3cret-token";

    private TestFrameworkService service;
    private MockMvc openApi;
    private MockMvc securedApi;

    @BeforeEach
    void setUp() {
        service = new TestFrameworkService(Set.of(
                TestSuiteFixtures.PassingSuite.class,
                TestSuiteFixtures.FailingSuite.class,
                TestSuiteFixtures.LongRunningSuite.class));
        openApi = MockMvcBuilders.standaloneSetup(new TestFrameworkController(service)).build();
        securedApi = MockMvcBuilders.standaloneSetup(new TestFrameworkController(service, TOKEN)).build();
    }

    // -------------------------------------------------------------------------
    // Gate
    // -------------------------------------------------------------------------

    @Test
    void gateReturnsOkWhenEveryTestPasses() throws Exception {
        openApi.perform(post("/api/test-framework/suites/Passing Suite/gate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.failedCount").value(0));
    }

    @Test
    void gateReturnsUnprocessableEntityWhenATestFails() throws Exception {
        openApi.perform(post("/api/test-framework/suites/Failing Suite/gate"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.failedCount").value(1))
                .andExpect(jsonPath("$.completedResults", hasSize(1)));
    }

    @Test
    void gateReturnsGatewayTimeoutAndCancelsTheRunWhenItDoesNotFinishInTime() throws Exception {
        openApi.perform(post("/api/test-framework/suites/Long Running Suite/gate").param("timeout", "PT0.2S"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void gateRejectsNonPositiveTimeout() throws Exception {
        openApi.perform(post("/api/test-framework/suites/Passing Suite/gate").param("timeout", "PT0S"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gateReturnsNotFoundForUnknownSuite() throws Exception {
        openApi.perform(post("/api/test-framework/suites/No Such Suite/gate"))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // Authentication
    // -------------------------------------------------------------------------

    @Test
    void statusEndpointDoesNotRequireTheToken() throws Exception {
        securedApi.perform(get("/api/test-framework/status"))
                .andExpect(status().isOk());
    }

    @Test
    void endpointsRejectRequestsWithoutTheToken() throws Exception {
        securedApi.perform(get("/api/test-framework/suites"))
                .andExpect(status().isUnauthorized());
        securedApi.perform(post("/api/test-framework/suites/Passing Suite/run"))
                .andExpect(status().isUnauthorized());
        securedApi.perform(post("/api/test-framework/suites/Passing Suite/gate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointsRejectAWrongToken() throws Exception {
        securedApi.perform(get("/api/test-framework/suites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointsRejectTheTokenWithoutTheBearerScheme() throws Exception {
        securedApi.perform(get("/api/test-framework/suites")
                        .header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointsAcceptTheCorrectBearerToken() throws Exception {
        securedApi.perform(get("/api/test-framework/suites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void gateRequiresTheTokenWhenConfigured() throws Exception {
        securedApi.perform(post("/api/test-framework/suites/Passing Suite/gate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk());
        securedApi.perform(post("/api/test-framework/suites/Passing Suite/gate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankTokenDisablesAuthentication() throws Exception {
        MockMvc blankToken = MockMvcBuilders.standaloneSetup(new TestFrameworkController(service, "  ")).build();

        blankToken.perform(get("/api/test-framework/suites"))
                .andExpect(status().isOk());
    }
}
