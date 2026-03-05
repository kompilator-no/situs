package no.testframework.javalibrary.api;

import no.testframework.javalibrary.model.TestCase;
import no.testframework.javalibrary.model.TestSuite;
import no.testframework.javalibrary.spring.TestFrameworkService;
import no.testframework.javalibrary.spring.model.SuiteRunStatus;
import no.testframework.javalibrary.fixtures.AsyncTestHelper;
import no.testframework.javalibrary.fixtures.TestSuiteFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFrameworkServiceTest {

    private TestFrameworkService service;
    private TestFrameworkService serviceWithTimeout;

    @BeforeEach
    void setUp() {
        service = new TestFrameworkService(
                Set.of(TestSuiteFixtures.ServiceSuite.class, TestSuiteFixtures.OtherSuite.class));
        serviceWithTimeout = new TestFrameworkService(
                Set.of(TestSuiteFixtures.TimeoutSuite.class));
    }

    // -------------------------------------------------------------------------
    // getAllSuites
    // -------------------------------------------------------------------------

    @Test
    void getAllSuitesReturnsBothSuites() {
        List<TestSuite> suites = service.getAllSuites();

        assertThat(suites).hasSize(2);
        assertThat(suites).extracting(TestSuite::getName)
                .containsExactlyInAnyOrder("Service Suite", "Other Suite");
    }

    @Test
    void getAllSuitesIncludesTestCases() {
        TestSuite suite = service.getAllSuites().stream()
                .filter(s -> s.getName().equals("Service Suite"))
                .findFirst().orElseThrow();

        assertThat(suite.getTests()).hasSize(2);
        assertThat(suite.getTests()).extracting(TestCase::getName)
                .containsExactlyInAnyOrder("passingTest", "failingTest");
    }

    @Test
    void getAllSuitesSuiteDescriptionIsPreserved() {
        TestSuite suite = service.getAllSuites().stream()
                .filter(s -> s.getName().equals("Service Suite"))
                .findFirst().orElseThrow();

        assertThat(suite.getDescription()).isEqualTo("Used by service tests");
    }

    // -------------------------------------------------------------------------
    // startSuiteAsync / getRunStatus
    // -------------------------------------------------------------------------

    @Test
    void startSuiteAsyncReturnsRunId() {
        String runId = service.startSuiteAsync("Service Suite");
        assertThat(runId).isNotBlank();
    }

    @Test
    void startSuiteAsyncInitialStatusIsPendingOrRunning() {
        String runId = service.startSuiteAsync("Service Suite");
        SuiteRunStatus status = service.getRunStatus(runId);
        assertThat(status.getStatus()).isIn(SuiteRunStatus.Status.PENDING, SuiteRunStatus.Status.RUNNING);
    }

    @Test
    void runSuiteReturnsCorrectSuiteName() throws InterruptedException {
        String runId = service.startSuiteAsync("Service Suite");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(service, runId);

        assertThat(result.getSuiteName()).isEqualTo("Service Suite");
    }

    @Test
    void runSuiteReportsPassAndFailCounts() throws InterruptedException {
        String runId = service.startSuiteAsync("Service Suite");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(service, runId);

        assertThat(result.getPassedCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
    }

    @Test
    void runSuiteResultsContainExpectedTestCases() throws InterruptedException {
        String runId = service.startSuiteAsync("Service Suite");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(service, runId);

        assertThat(result.getCompletedResults()).hasSize(2);
        assertThat(result.getCompletedResults()).extracting(r -> r.getName())
                .containsExactlyInAnyOrder("passingTest", "failingTest");
    }

    @Test
    void startSuiteAsyncThrowsForUnknownSuite() {
        assertThatThrownBy(() -> service.startSuiteAsync("Does Not Exist"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Does Not Exist");
    }

    // -------------------------------------------------------------------------
    // startSingleTestAsync
    // -------------------------------------------------------------------------

    @Test
    void runSingleTestPassingTestReturnsPassedResult() throws InterruptedException {
        String runId = service.startSingleTestAsync("Service Suite", "passingTest");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(service, runId);

        assertThat(result.getCompletedResults()).hasSize(1);
        assertThat(result.getCompletedResults().get(0).isPassed()).isTrue();
        assertThat(result.getCompletedResults().get(0).getErrorMessage()).isNull();
    }

    @Test
    void runSingleTestFailingTestReturnsFailedResult() throws InterruptedException {
        String runId = service.startSingleTestAsync("Service Suite", "failingTest");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(service, runId);

        assertThat(result.getCompletedResults().get(0).isPassed()).isFalse();
        assertThat(result.getCompletedResults().get(0).getErrorMessage()).isNotBlank();
    }

    @Test
    void runSingleTestThrowsForUnknownTest() {
        assertThatThrownBy(() -> service.startSingleTestAsync("Service Suite", "noSuchTest"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("noSuchTest");
    }

    @Test
    void runSingleTestThrowsForUnknownSuite() {
        assertThatThrownBy(() -> service.startSingleTestAsync("Ghost Suite", "passingTest"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ghost Suite");
    }

    @Test
    void getRunStatusThrowsForUnknownRunId() {
        assertThatThrownBy(() -> service.getRunStatus("no-such-run"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-such-run");
    }

    // -------------------------------------------------------------------------
    // Long-running / timeout tests
    // -------------------------------------------------------------------------

    @Test
    void runSuiteWithTimedOutTestReportsFailure() throws InterruptedException {
        String runId = serviceWithTimeout.startSuiteAsync("Timeout Suite");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(serviceWithTimeout, runId);

        assertThat(result.getPassedCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
    }

    @Test
    void runSuiteTimedOutTestResultHasTimeoutMessage() throws InterruptedException {
        String runId = serviceWithTimeout.startSuiteAsync("Timeout Suite");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(serviceWithTimeout, runId);

        assertThat(result.getCompletedResults()).anySatisfy(r -> {
            assertThat(r.getName()).isEqualTo("slowTest");
            assertThat(r.isPassed()).isFalse();
            assertThat(r.getErrorMessage()).contains("timed out");
        });
    }

    @Test
    void runSuiteTimedOutTestDurationIsBoundedByTimeout() throws InterruptedException {
        String runId = serviceWithTimeout.startSuiteAsync("Timeout Suite");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(serviceWithTimeout, runId);

        assertThat(result.getCompletedResults()).anySatisfy(r -> {
            if (r.getName().equals("slowTest")) {
                assertThat(r.getDurationMs()).isLessThan(2_000);
            }
        });
    }

    @Test
    void runSingleTimedOutTestReturnsFailedResult() throws InterruptedException {
        String runId = serviceWithTimeout.startSingleTestAsync("Timeout Suite", "slowTest");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(serviceWithTimeout, runId);

        assertThat(result.getCompletedResults().get(0).isPassed()).isFalse();
        assertThat(result.getCompletedResults().get(0).getErrorMessage()).contains("timed out");
        assertThat(result.getCompletedResults().get(0).getDurationMs()).isLessThan(2_000);
    }

    @Test
    void runSingleFastTestInTimeoutSuitePasses() throws InterruptedException {
        String runId = serviceWithTimeout.startSingleTestAsync("Timeout Suite", "fastTest");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(serviceWithTimeout, runId);

        assertThat(result.getCompletedResults().get(0).isPassed()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Dependency injection via ApplicationContext constructor
    // -------------------------------------------------------------------------

    @Configuration
    static class DiTestConfig {
        @Bean
        public TestSuiteFixtures.GreetingService greetingService() {
            return new TestSuiteFixtures.GreetingService("Hello");
        }

        @Bean
        public TestSuiteFixtures.DiSuite diSuite(TestSuiteFixtures.GreetingService svc) {
            return new TestSuiteFixtures.DiSuite(svc);
        }
    }

    @Test
    void serviceWithApplicationContextResolvesBeansAndInjectsDependencies() throws InterruptedException {
        ApplicationContext ctx = new AnnotationConfigApplicationContext(DiTestConfig.class);
        TestFrameworkService diService = new TestFrameworkService(ctx,
                Set.of(TestSuiteFixtures.DiSuite.class));

        String runId = diService.startSuiteAsync("DI Suite");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(diService, runId);

        assertThat(result.getPassedCount())
                .as("DiSuite test should pass when GreetingService is injected via Spring context")
                .isEqualTo(1L);
        assertThat(result.getFailedCount()).isEqualTo(0L);
    }

    @Test
    void serviceWithApplicationContextNonBeanFallsBackToReflection() throws InterruptedException {
        ApplicationContext ctx = new AnnotationConfigApplicationContext(DiTestConfig.class);
        TestSuiteFixtures.NonBeanSuite.wasInstantiated = false;

        TestFrameworkService diService = new TestFrameworkService(ctx,
                Set.of(TestSuiteFixtures.NonBeanSuite.class));

        String runId = diService.startSuiteAsync("Non-Bean Suite");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(diService, runId);

        assertThat(TestSuiteFixtures.NonBeanSuite.wasInstantiated)
                .as("Non-bean suite must be instantiated via reflection fallback")
                .isTrue();
        assertThat(result.getPassedCount()).isEqualTo(1L);
    }

    @Test
    void serviceWithApplicationContextSuiteAppearsInGetAllSuites() {
        ApplicationContext ctx = new AnnotationConfigApplicationContext(DiTestConfig.class);
        TestFrameworkService diService = new TestFrameworkService(ctx,
                Set.of(TestSuiteFixtures.DiSuite.class));

        List<TestSuite> suites = diService.getAllSuites();

        assertThat(suites).hasSize(1);
        assertThat(suites.get(0).getName()).isEqualTo("DI Suite");
    }

    // -------------------------------------------------------------------------
    // Retries via service layer
    // -------------------------------------------------------------------------

    @Test
    void retryPassSuiteIsRecordedAsPassedInService() throws InterruptedException {
        TestSuiteFixtures.RetryPassSuite.callCount.set(0);
        TestFrameworkService retryService = new TestFrameworkService(
                Set.of(TestSuiteFixtures.RetryPassSuite.class));

        String runId = retryService.startSuiteAsync("Retry Pass Suite");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(retryService, runId);

        assertThat(result.getPassedCount()).isEqualTo(1L);
        assertThat(result.getFailedCount()).isEqualTo(0L);
    }

    @Test
    void retryPassSuiteResultHasCorrectAttemptCount() throws InterruptedException {
        TestSuiteFixtures.RetryPassSuite.callCount.set(0);
        TestFrameworkService retryService = new TestFrameworkService(
                Set.of(TestSuiteFixtures.RetryPassSuite.class));

        String runId = retryService.startSuiteAsync("Retry Pass Suite");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(retryService, runId);

        assertThat(result.getCompletedResults()).hasSize(1);
        assertThat(result.getCompletedResults().get(0).getAttempts())
                .as("Should have taken 3 attempts (fails on 1 and 2, passes on 3)")
                .isEqualTo(3);
    }

    @Test
    void retryExhaustedSuiteIsRecordedAsFailedInService() throws InterruptedException {
        TestSuiteFixtures.RetryExhaustedSuite.callCount.set(0);
        TestFrameworkService retryService = new TestFrameworkService(
                Set.of(TestSuiteFixtures.RetryExhaustedSuite.class));

        String runId = retryService.startSuiteAsync("Retry Exhausted Suite");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(retryService, runId);

        assertThat(result.getPassedCount()).isEqualTo(0L);
        assertThat(result.getFailedCount()).isEqualTo(1L);
        assertThat(result.getCompletedResults().get(0).getAttempts()).isEqualTo(3);
    }

    @Test
    void retryNotNeededSuiteHasOneAttemptInResult() throws InterruptedException {
        TestSuiteFixtures.RetryNotNeededSuite.callCount.set(0);
        TestFrameworkService retryService = new TestFrameworkService(
                Set.of(TestSuiteFixtures.RetryNotNeededSuite.class));

        String runId = retryService.startSuiteAsync("Retry Not Needed Suite");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(retryService, runId);

        assertThat(result.getPassedCount()).isEqualTo(1L);
        assertThat(result.getCompletedResults().get(0).getAttempts())
                .as("Passed first time — attempts should be 1")
                .isEqualTo(1);
    }
}
