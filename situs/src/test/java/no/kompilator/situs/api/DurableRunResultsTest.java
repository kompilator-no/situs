package no.kompilator.situs.api;

import no.kompilator.situs.fixtures.AsyncTestHelper;
import no.kompilator.situs.fixtures.TestSuiteFixtures;
import no.kompilator.situs.model.SuiteRunStatus;
import no.kompilator.situs.runtime.InstanceFactory;
import no.kompilator.situs.service.FileRunStore;
import no.kompilator.situs.service.TestFrameworkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DurableRunResultsTest {

    @TempDir
    Path resultDirectory;

    @Test
    void finishedRunIsReadableAfterTheServiceRestarts() throws Exception {
        String runId;
        try (TestFrameworkService before = service()) {
            runId = before.startSuiteAsync("Passing Suite");
            AsyncTestHelper.awaitCompleted(before, runId);
        }

        try (TestFrameworkService after = service()) {
            SuiteRunStatus status = after.getRunStatus(runId);

            assertThat(status.getStatus()).isEqualTo(SuiteRunStatus.Status.COMPLETED);
            assertThat(status.getPassedCount()).isEqualTo(1);
            assertThat(status.getCompletedResults()).hasSize(1);
        }
    }

    @Test
    void awaitRunReturnsTheTerminalSnapshotWhenTheRunFinishesInTime() throws Exception {
        try (TestFrameworkService service = service()) {
            String runId = service.startSuiteAsync("Passing Suite");

            SuiteRunStatus status = service.awaitRun(runId, Duration.ofSeconds(10));

            assertThat(status.getStatus().isTerminal()).isTrue();
            assertThat(status.getStatus()).isEqualTo(SuiteRunStatus.Status.COMPLETED);
        }
    }

    @Test
    void awaitRunReturnsTheLiveSnapshotWhenTheTimeoutElapses() throws Exception {
        try (TestFrameworkService service = service()) {
            String runId = service.startSuiteAsync("Long Running Suite");

            SuiteRunStatus status = service.awaitRun(runId, Duration.ofMillis(100));

            assertThat(status.getStatus().isTerminal()).isFalse();
            service.cancelRun(runId);
        }
    }

    @Test
    void cancelledRunIsKeptAfterCancellation() throws Exception {
        String runId;
        try (TestFrameworkService before = service()) {
            runId = before.startSuiteAsync("Long Running Suite");
            before.cancelRun(runId);
        }

        try (TestFrameworkService after = service()) {
            assertThat(after.getRunStatus(runId).getStatus()).isEqualTo(SuiteRunStatus.Status.CANCELLED);
        }
    }

    private TestFrameworkService service() {
        return new TestFrameworkService(
                InstanceFactory.reflective(),
                Set.of(TestSuiteFixtures.PassingSuite.class, TestSuiteFixtures.LongRunningSuite.class),
                new FileRunStore(resultDirectory, 10));
    }
}
