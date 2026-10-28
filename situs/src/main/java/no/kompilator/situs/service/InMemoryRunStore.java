package no.kompilator.situs.service;

import no.kompilator.situs.model.SuiteRunStatus;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@link RunStore} that keeps finished runs in memory only. Results are lost on restart.
 *
 * <p>This is the default store. When the limit is reached, the oldest saved run is evicted.
 */
public final class InMemoryRunStore implements RunStore {

    private final int maxStoredRuns;
    private final Map<String, SuiteRunStatus> runs = new LinkedHashMap<>();

    /**
     * @param maxStoredRuns maximum number of terminal runs retained, must be at least 1
     */
    public InMemoryRunStore(int maxStoredRuns) {
        if (maxStoredRuns < 1) {
            throw new IllegalArgumentException("maxStoredRuns must be at least 1");
        }
        this.maxStoredRuns = maxStoredRuns;
    }

    @Override
    public synchronized void save(SuiteRunStatus status) {
        runs.put(status.getRunId(), status);
        Iterator<String> oldest = runs.keySet().iterator();
        while (runs.size() > maxStoredRuns) {
            oldest.next();
            oldest.remove();
        }
    }

    @Override
    public synchronized Optional<SuiteRunStatus> find(String runId) {
        return Optional.ofNullable(runs.get(runId));
    }
}
