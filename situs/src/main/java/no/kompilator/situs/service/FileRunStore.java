package no.kompilator.situs.service;

import no.kompilator.situs.model.SuiteRunStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * {@link RunStore} that writes each finished run to its own file in a directory, so results
 * survive restarts and can be read by a new service instance.
 *
 * <p>Snapshots are stored with Java serialization. Only point this at a directory that only
 * the application can write to. Reads are restricted to classes from the Situs model and
 * {@code java.base}; unreadable or unexpected files are logged and treated as missing.
 *
 * <p>Retention is enforced by modification time: when a run is saved and the directory holds
 * more than {@code maxStoredRuns} runs, the oldest files are deleted.
 */
public final class FileRunStore implements RunStore {

    private static final Logger log = LoggerFactory.getLogger(FileRunStore.class);
    private static final String SUFFIX = ".ser";
    private static final Pattern RUN_ID = Pattern.compile("[0-9a-fA-F-]{1,64}");
    private static final ObjectInputFilter INPUT_FILTER = ObjectInputFilter.Config.createFilter(
            "no.kompilator.situs.model.*;java.base/*;!*");

    private final Path directory;
    private final int maxStoredRuns;

    /**
     * @param directory      directory that holds one file per run; created if it does not exist
     * @param maxStoredRuns  maximum number of terminal runs retained, must be at least 1
     * @throws UncheckedIOException if the directory cannot be created
     */
    public FileRunStore(Path directory, int maxStoredRuns) {
        if (maxStoredRuns < 1) {
            throw new IllegalArgumentException("maxStoredRuns must be at least 1");
        }
        this.directory = directory;
        this.maxStoredRuns = maxStoredRuns;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create run result directory: " + directory, e);
        }
    }

    @Override
    public synchronized void save(SuiteRunStatus status) {
        String runId = status.getRunId();
        if (!RUN_ID.matcher(runId).matches()) {
            throw new IllegalArgumentException("Invalid run id: " + runId);
        }
        Path target = directory.resolve(runId + SUFFIX);
        Path temp = directory.resolve(runId + ".tmp");
        try {
            try (OutputStream file = Files.newOutputStream(temp);
                 ObjectOutputStream out = new ObjectOutputStream(file)) {
                out.writeObject(status);
            }
            Files.move(temp, target, REPLACE_EXISTING, ATOMIC_MOVE);
            prune();
        } catch (IOException | UncheckedIOException e) {
            log.error("Could not persist run result: runId={}", runId, e);
        }
    }

    @Override
    public synchronized Optional<SuiteRunStatus> find(String runId) {
        if (!RUN_ID.matcher(runId).matches()) {
            return Optional.empty();
        }
        Path file = directory.resolve(runId + SUFFIX);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (InputStream raw = Files.newInputStream(file);
             ObjectInputStream in = new ObjectInputStream(raw)) {
            in.setObjectInputFilter(INPUT_FILTER);
            return Optional.of((SuiteRunStatus) in.readObject());
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            log.warn("Ignoring unreadable run result file: {}", file, e);
            return Optional.empty();
        }
    }

    private void prune() throws IOException {
        List<Path> runFiles;
        try (Stream<Path> files = Files.list(directory)) {
            runFiles = files
                    .filter(path -> path.getFileName().toString().endsWith(SUFFIX))
                    .sorted(Comparator.comparing(FileRunStore::lastModified).reversed())
                    .toList();
        }
        for (Path stale : runFiles.subList(Math.min(maxStoredRuns, runFiles.size()), runFiles.size())) {
            Files.deleteIfExists(stale);
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
