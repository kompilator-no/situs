package no.kompilator.situs.service;

import no.kompilator.situs.model.SuiteRunStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileRunStoreTest {

    @TempDir
    Path directory;

    @Test
    void savedRunCanBeReadByAnotherStoreInstance() {
        SuiteRunStatus run = completedRun("Passing Suite");

        new FileRunStore(directory, 10).save(run);
        SuiteRunStatus found = new FileRunStore(directory, 10).find(run.getRunId()).orElseThrow();

        assertThat(found.getRunId()).isEqualTo(run.getRunId());
        assertThat(found.getSuiteName()).isEqualTo("Passing Suite");
        assertThat(found.getStatus()).isEqualTo(SuiteRunStatus.Status.COMPLETED);
        assertThat(found.getPassedCount()).isEqualTo(1);
    }

    @Test
    void unknownRunIdIsEmpty() {
        assertThat(new FileRunStore(directory, 10).find(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void runIdThatIsNotAUuidIsNeverLookedUpOnDisk() throws Exception {
        Path outside = directory.resolve("outside.ser");
        Files.writeString(outside, "not a snapshot", StandardCharsets.UTF_8);
        Path store = Files.createDirectories(directory.resolve("store"));

        assertThat(new FileRunStore(store, 10).find("../outside")).isEmpty();
    }

    @Test
    void unreadableFileIsTreatedAsMissing() throws Exception {
        String runId = UUID.randomUUID().toString();
        Files.writeString(directory.resolve(runId + ".ser"), "garbage", StandardCharsets.UTF_8);

        assertThat(new FileRunStore(directory, 10).find(runId)).isEmpty();
    }

    @Test
    void saveRejectsRunIdThatIsNotAUuid() {
        FileRunStore store = new FileRunStore(directory, 10);
        SuiteRunStatus run = new SuiteRunStatus("../escape", "Suite", SuiteRunStatus.Status.COMPLETED,
                List.of(), 0, 0, 0, 0, 0, 0);

        assertThatThrownBy(() -> store.save(run)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oldestRunsAreDeletedBeyondTheRetentionLimit() throws Exception {
        FileRunStore store = new FileRunStore(directory, 2);
        long base = System.currentTimeMillis() - 100_000;
        SuiteRunStatus first = completedRun("first");
        SuiteRunStatus second = completedRun("second");
        SuiteRunStatus third = completedRun("third");

        store.save(first);
        setModifiedTime(first, base);
        store.save(second);
        setModifiedTime(second, base + 1_000);
        store.save(third);

        assertThat(store.find(first.getRunId())).isEmpty();
        assertThat(store.find(second.getRunId())).isPresent();
        assertThat(store.find(third.getRunId())).isPresent();
    }

    @Test
    void rejectsNonPositiveRetention() {
        assertThatThrownBy(() -> new FileRunStore(directory, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private void setModifiedTime(SuiteRunStatus run, long epochMs) throws Exception {
        Files.setLastModifiedTime(directory.resolve(run.getRunId() + ".ser"), FileTime.fromMillis(epochMs));
    }

    private static SuiteRunStatus completedRun(String suiteName) {
        return new SuiteRunStatus(UUID.randomUUID().toString(), suiteName, SuiteRunStatus.Status.COMPLETED,
                List.of(), 1, 1, 1, 0, 0, 0);
    }
}
