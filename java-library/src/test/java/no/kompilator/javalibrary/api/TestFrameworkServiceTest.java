package no.kompilator.javalibrary.api;

import no.kompilator.javalibrary.model.TestCase;
import no.kompilator.javalibrary.model.TestSuite;
import no.kompilator.javalibrary.model.SuiteRunStatus;
import no.kompilator.javalibrary.plugin.TestCompletedEvent;
import no.kompilator.javalibrary.service.AlreadyRunningException;
import no.kompilator.javalibrary.service.TestFrameworkService;
import no.kompilator.javalibrary.spring.SpringInstanceFactory;
import no.kompilator.javalibrary.fixtures.AsyncTestHelper;
import no.kompilator.javalibrary.fixtures.TestSuiteFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;

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

        assertThat(result.getCompletedCount()).isEqualTo(2);
        assertThat(result.getTotalCount()).isEqualTo(2);
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
    void runSuiteResultsExposePerTestTimestamps() throws InterruptedException {
        String runId = service.startSuiteAsync("Service Suite");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(service, runId);

        assertThat(result.getRunStartedAtEpochMs()).isPositive();
        assertThat(result.getLastUpdatedAtEpochMs()).isGreaterThanOrEqualTo(result.getRunStartedAtEpochMs());
        assertThat(result.getCompletedResults())
                .allSatisfy(testCaseResult -> {
                    assertThat(testCaseResult.getStartedAtEpochMs()).isPositive();
                    assertThat(testCaseResult.getCompletedAtEpochMs())
                            .isGreaterThanOrEqualTo(testCaseResult.getStartedAtEpochMs());
                    assertThat(testCaseResult.getDurationMs()).isGreaterThanOrEqualTo(0);
                });
    }

    @Test
    void runningSuitePublishesPartialResultsBeforeCompletion() throws InterruptedException {
        TestFrameworkService slowService = new TestFrameworkService(Set.of(TestSuiteFixtures.SlowSuite.class));
        String runId = slowService.startSuiteAsync("Slow Suite");

        SuiteRunStatus partial = null;
        for (int i = 0; i < 20; i++) {
            Thread.sleep(50);
            SuiteRunStatus current = slowService.getRunStatus(runId);
            if (current.getStatus() == SuiteRunStatus.Status.RUNNING && !current.getCompletedResults().isEmpty()) {
                partial = current;
                break;
            }
        }

        assertThat(partial).isNotNull();
        assertThat(partial.getStatus()).isEqualTo(SuiteRunStatus.Status.RUNNING);
        assertThat(partial.getCompletedResults()).hasSize(1);
        assertThat(partial.getCompletedCount()).isEqualTo(1);
        assertThat(partial.getTotalCount()).isEqualTo(2);
        assertThat(partial.getPassedCount()).isEqualTo(1);
        assertThat(partial.getFailedCount()).isZero();
        assertThat(partial.getRunStartedAtEpochMs()).isPositive();
        assertThat(partial.getLastUpdatedAtEpochMs()).isGreaterThanOrEqualTo(partial.getRunStartedAtEpochMs());
        assertThat(partial.getCompletedResults().get(0).getStartedAtEpochMs()).isPositive();
        assertThat(partial.getCompletedResults().get(0).getCompletedAtEpochMs())
                .isGreaterThanOrEqualTo(partial.getCompletedResults().get(0).getStartedAtEpochMs());
    }

    @Test
    void startSuiteAsyncThrowsForUnknownSuite() {
        assertThatThrownBy(() -> service.startSuiteAsync("Does Not Exist"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Does Not Exist");
    }

    @Test
    void cancelRunMarksRunningSuiteAsCancelled() throws InterruptedException {
        TestFrameworkService cancellableService = new TestFrameworkService(Set.of(TestSuiteFixtures.LongRunningSuite.class));
        String runId = cancellableService.startSuiteAsync("Long Running Suite");

        Thread.sleep(100);
        SuiteRunStatus cancelled = cancellableService.cancelRun(runId);

        assertThat(cancelled.getStatus()).isEqualTo(SuiteRunStatus.Status.CANCELLED);
        assertThat(cancelled.getRunErrorMessage()).contains("Run cancelled");
        assertThat(cancelled.getRunErrorType()).isEqualTo(java.util.concurrent.CancellationException.class.getName());
    }

    @Test
    void cancelledSuiteCanBeStartedAgain() throws InterruptedException {
        TestFrameworkService cancellableService = new TestFrameworkService(Set.of(TestSuiteFixtures.LongRunningSuite.class));
        String firstRunId = cancellableService.startSuiteAsync("Long Running Suite");

        Thread.sleep(100);
        SuiteRunStatus cancelled = cancellableService.cancelRun(firstRunId);
        assertThat(cancelled.getStatus()).isEqualTo(SuiteRunStatus.Status.CANCELLED);

        String secondRunId = cancellableService.startSuiteAsync("Long Running Suite");
        SuiteRunStatus secondResult = AsyncTestHelper.awaitCompleted(cancellableService, secondRunId);

        assertThat(secondResult.getStatus()).isEqualTo(SuiteRunStatus.Status.COMPLETED);
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
    void runSingleTestDoesNotExecuteOtherTestsInSuite() throws InterruptedException {
        TestSuiteFixtures.CountingSuite.firstCount.set(0);
        TestSuiteFixtures.CountingSuite.secondCount.set(0);
        TestFrameworkService countingService = new TestFrameworkService(Set.of(TestSuiteFixtures.CountingSuite.class));

        String runId = countingService.startSingleTestAsync("Counting Suite", "secondTest");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(countingService, runId);

        assertThat(result.getStatus()).isEqualTo(SuiteRunStatus.Status.COMPLETED);
        assertThat(result.getCompletedResults()).hasSize(1);
        assertThat(result.getCompletedResults().get(0).getName()).isEqualTo("secondTest");
        assertThat(TestSuiteFixtures.CountingSuite.firstCount.get()).isZero();
        assertThat(TestSuiteFixtures.CountingSuite.secondCount.get()).isEqualTo(1);
    }

    @Test
    void getRunStatusThrowsForUnknownRunId() {
        assertThatThrownBy(() -> service.getRunStatus("no-such-run"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-such-run");
    }

    @Test
    void cancelRunThrowsForUnknownRunId() {
        assertThatThrownBy(() -> service.cancelRun("no-such-run"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-such-run");
    }

    @Test
    void suiteLifecycleFailureProducesFailedTerminalStatus() throws InterruptedException {
        TestFrameworkService failingService = new TestFrameworkService(
                Set.of(TestSuiteFixtures.BrokenBeforeAllSuite.class));

        String runId = failingService.startSuiteAsync("Broken BeforeAll Suite");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(failingService, runId);

        assertThat(result.getStatus()).isEqualTo(SuiteRunStatus.Status.FAILED);
        assertThat(result.getRunErrorMessage()).contains("beforeAll failed");
        assertThat(result.getRunErrorType()).isEqualTo(RuntimeException.class.getName());
        assertThat(result.getCompletedResults()).isEmpty();
    }

    @Test
    void listenerErrorProducesFailedTerminalStatus() throws InterruptedException {
        TestFrameworkService failingService = new TestFrameworkService(
                Set.of(TestSuiteFixtures.ServiceSuite.class));
        failingService.addListener(event -> {
            throw new AssertionError("listener boom");
        });

        String runId = failingService.startSuiteAsync("Service Suite");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(failingService, runId);

        assertThat(result.getStatus()).isEqualTo(SuiteRunStatus.Status.FAILED);
        assertThat(result.getRunErrorMessage()).contains("listener boom");
        assertThat(result.getRunErrorType()).isEqualTo(AssertionError.class.getName());
    }

    @Test
    void listenerExceptionDoesNotPreventLaterListenersFromRunning() throws InterruptedException {
        TestFrameworkService listenerService = new TestFrameworkService(
                Set.of(TestSuiteFixtures.ServiceSuite.class));
        AtomicInteger invocationCount = new AtomicInteger(0);

        listenerService.addListener(event -> {
            invocationCount.incrementAndGet();
            throw new RuntimeException("expected listener exception");
        });
        listenerService.addListener(event -> invocationCount.incrementAndGet());

        String runId = listenerService.startSuiteAsync("Service Suite");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(listenerService, runId);

        assertThat(result.getStatus()).isEqualTo(SuiteRunStatus.Status.COMPLETED);
        assertThat(invocationCount.get()).isEqualTo(2);
    }

    @Test
    void listenerErrorFailsRunAndStopsLaterListeners() throws InterruptedException {
        TestFrameworkService listenerService = new TestFrameworkService(
                Set.of(TestSuiteFixtures.ServiceSuite.class));
        AtomicInteger invocationCount = new AtomicInteger(0);

        listenerService.addListener(event -> {
            invocationCount.incrementAndGet();
            throw new AssertionError("fatal listener error");
        });
        listenerService.addListener(event -> invocationCount.incrementAndGet());

        String runId = listenerService.startSuiteAsync("Service Suite");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(listenerService, runId);

        assertThat(result.getStatus()).isEqualTo(SuiteRunStatus.Status.FAILED);
        assertThat(result.getRunErrorMessage()).contains("fatal listener error");
        assertThat(result.getRunErrorType()).isEqualTo(AssertionError.class.getName());
        assertThat(invocationCount.get()).isEqualTo(1);
    }

    @Test
    void listenerReceivesPerTestCompletionEventsDuringSuiteRun() throws InterruptedException {
        TestFrameworkService listenerService = new TestFrameworkService(
                Set.of(TestSuiteFixtures.ServiceSuite.class));
        List<TestCompletedEvent> events = new CopyOnWriteArrayList<>();

        listenerService.addListener(new no.kompilator.javalibrary.plugin.SuiteRunListener() {
            @Override
            public void onTestCompleted(TestCompletedEvent event) {
                events.add(event);
            }

            @Override
            public void onSuiteCompleted(no.kompilator.javalibrary.plugin.SuiteCompletedEvent event) {
                // no-op
            }
        });

        String runId = listenerService.startSuiteAsync("Service Suite");
        SuiteRunStatus result = AsyncTestHelper.awaitCompleted(listenerService, runId);

        assertThat(result.getStatus()).isEqualTo(SuiteRunStatus.Status.COMPLETED);
        assertThat(events).hasSize(2);
        assertThat(events).extracting(TestCompletedEvent::runId).containsOnly(runId);
        assertThat(events).extracting(event -> event.suite().getName()).containsOnly("Service Suite");
        assertThat(events).extracting(event -> event.testResult().getName())
                .containsExactlyInAnyOrder("passingTest", "failingTest");
        assertThat(events)
                .extracting(event -> event.testResult().getStartedAtEpochMs())
                .allMatch(timestamp -> (Long) timestamp > 0);
    }

    @Test
    void getRunStatusReturnsDefensiveSnapshot() throws InterruptedException {
        String runId = service.startSuiteAsync("Service Suite");
        SuiteRunStatus firstSnapshot = AsyncTestHelper.awaitCompleted(service, runId);

        firstSnapshot.setStatus(SuiteRunStatus.Status.RUNNING);
        assertThatThrownBy(() -> firstSnapshot.getCompletedResults().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        SuiteRunStatus secondSnapshot = service.getRunStatus(runId);
        assertThat(secondSnapshot.getStatus()).isEqualTo(SuiteRunStatus.Status.COMPLETED);
        assertThat(secondSnapshot.getCompletedResults()).hasSize(2);
    }

    @Test
    void constructorCopiesRegisteredSuitesDefensively() {
        Set<Class<?>> mutableSuites = new HashSet<>();
        mutableSuites.add(TestSuiteFixtures.ServiceSuite.class);

        TestFrameworkService copiedService = new TestFrameworkService(mutableSuites);
        mutableSuites.add(TestSuiteFixtures.OtherSuite.class);

        assertThat(copiedService.getAllSuites())
                .extracting(TestSuite::getName)
                .containsExactly("Service Suite");
    }

    @Test
    void closedServiceRejectsNewRuns() {
        TestFrameworkService closableService = new TestFrameworkService(
                Set.of(TestSuiteFixtures.ServiceSuite.class));
        closableService.close();

        assertThatThrownBy(() -> closableService.startSuiteAsync("Service Suite"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot accept new runs");
    }

    @Test
    void duplicateSuiteNamesFailAtServiceStartup() {
        assertThatThrownBy(() -> new TestFrameworkService(Set.of(
                TestSuiteFixtures.DuplicateNamedSuiteA.class,
                TestSuiteFixtures.DuplicateNamedSuiteB.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate suite name(s)")
                .hasMessageContaining("Duplicate Service Suite");
    }

    @Test
    void duplicateTestNamesFailAtServiceStartup() {
        assertThatThrownBy(() -> new TestFrameworkService(Set.of(
                TestSuiteFixtures.DuplicateNamedTestsSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate test name(s)")
                .hasMessageContaining("duplicateTest");
    }

    @Test
    void invalidTimeoutConfigurationFailsAtServiceStartup() {
        assertThatThrownBy(() -> new TestFrameworkService(Set.of(
                TestSuiteFixtures.InvalidTimeoutSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid timeoutMs");
    }

    @Test
    void invalidDelayConfigurationFailsAtServiceStartup() {
        assertThatThrownBy(() -> new TestFrameworkService(Set.of(
                TestSuiteFixtures.InvalidDelaySuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid delayMs");
    }

    @Test
    void invalidRetryConfigurationFailsAtServiceStartup() {
        assertThatThrownBy(() -> new TestFrameworkService(Set.of(
                TestSuiteFixtures.InvalidRetrySuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid retries");
    }

    @Test
    void invalidTestMethodParametersFailAtServiceStartup() {
        assertThatThrownBy(() -> new TestFrameworkService(Set.of(
                TestSuiteFixtures.ParameterizedTestSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Test method")
                .hasMessageContaining("must not declare parameters");
    }

    @Test
    void invalidLifecycleMethodVisibilityFailsAtServiceStartup() {
        assertThatThrownBy(() -> new TestFrameworkService(Set.of(
                TestSuiteFixtures.PrivateBeforeAllSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@BeforeAll method")
                .hasMessageContaining("must be public");
    }

    @Test
    void terminalRunsAreEvictedWhenHistoryLimitIsReached() throws InterruptedException {
        TestFrameworkService limitedService = new TestFrameworkService(
                Set.of(TestSuiteFixtures.ServiceSuite.class), 2);

        String firstRunId = limitedService.startSuiteAsync("Service Suite");
        AsyncTestHelper.awaitCompleted(limitedService, firstRunId);

        String secondRunId = limitedService.startSuiteAsync("Service Suite");
        AsyncTestHelper.awaitCompleted(limitedService, secondRunId);

        String thirdRunId = limitedService.startSuiteAsync("Service Suite");
        AsyncTestHelper.awaitCompleted(limitedService, thirdRunId);

        assertThatThrownBy(() -> limitedService.getRunStatus(firstRunId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(firstRunId);
        assertThat(limitedService.getRunStatus(secondRunId).getStatus()).isEqualTo(SuiteRunStatus.Status.COMPLETED);
        assertThat(limitedService.getRunStatus(thirdRunId).getStatus()).isEqualTo(SuiteRunStatus.Status.COMPLETED);
    }

    @Test
    void pollingOldRunEventuallyFailsOnceEvictedByHistoryLimit() throws InterruptedException {
        TestFrameworkService limitedService = new TestFrameworkService(
                Set.of(TestSuiteFixtures.SlowSuite.class), 1);

        String firstRunId = limitedService.startSuiteAsync("Slow Suite");
        AsyncTestHelper.awaitCompleted(limitedService, firstRunId);

        String secondRunId = limitedService.startSuiteAsync("Slow Suite");

        boolean firstRunEvicted = false;
        for (int i = 0; i < 50; i++) {
            Thread.sleep(100);
            try {
                limitedService.getRunStatus(firstRunId);
            } catch (IllegalArgumentException ex) {
                firstRunEvicted = true;
                break;
            }
        }

        SuiteRunStatus secondResult = AsyncTestHelper.awaitCompleted(limitedService, secondRunId);

        assertThat(firstRunEvicted).isTrue();
        assertThat(secondResult.getStatus()).isEqualTo(SuiteRunStatus.Status.COMPLETED);
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

    @Test
    void startingSameSingleTestWhileItIsRunningThrowsAlreadyRunning() {
        TestFrameworkService slowService = new TestFrameworkService(Set.of(TestSuiteFixtures.TimeoutSuite.class));
        slowService.startSingleTestAsync("Timeout Suite", "slowTest");

        assertThatThrownBy(() -> slowService.startSingleTestAsync("Timeout Suite", "slowTest"))
                .isInstanceOf(AlreadyRunningException.class)
                .hasMessageContaining("Timeout Suite/slowTest");
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
        TestFrameworkService diService = new TestFrameworkService(new SpringInstanceFactory(ctx),
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

        TestFrameworkService diService = new TestFrameworkService(new SpringInstanceFactory(ctx),
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
        TestFrameworkService diService = new TestFrameworkService(new SpringInstanceFactory(ctx),
                Set.of(TestSuiteFixtures.DiSuite.class));

        List<TestSuite> suites = diService.getAllSuites();

        assertThat(suites).hasSize(1);
        assertThat(suites.get(0).getName()).isEqualTo("DI Suite");
    }

    @Test
    void closeCanBeCalledMoreThanOnce() {
        TestFrameworkService closableService = new TestFrameworkService(Set.of(TestSuiteFixtures.ServiceSuite.class));

        closableService.close();
        closableService.close();

        assertThatThrownBy(() -> closableService.startSuiteAsync("Service Suite"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot accept new runs");
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
