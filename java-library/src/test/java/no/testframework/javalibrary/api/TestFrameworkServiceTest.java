package no.testframework.javalibrary.api;

import no.testframework.javalibrary.model.TestCase;
import no.testframework.javalibrary.model.TestSuite;
import no.testframework.javalibrary.spring.TestFrameworkService;
import no.testframework.javalibrary.spring.model.SuiteRunStatus;
import no.testframework.javalibrary.fixtures.AsyncTestHelper;
import no.testframework.javalibrary.fixtures.TestSuiteFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
