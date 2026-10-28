package no.kompilator.situs.service;

import no.kompilator.situs.model.SuiteRunStatus;

import java.util.Optional;

/**
 * Storage for runs that have reached a terminal state.
 *
 * <p>{@link TestFrameworkService} keeps runs that are still {@code PENDING} or {@code RUNNING}
 * in memory and hands each run to the store once it finishes. Implementations decide how
 * long finished runs are kept and whether they survive a restart.
 *
 * @see InMemoryRunStore
 * @see FileRunStore
 */
public interface RunStore {

    /**
     * Stores a terminal snapshot. Implementations evict the oldest entries beyond their
     * retention limit.
     *
     * @param status a snapshot whose {@link SuiteRunStatus.Status#isTerminal()} is {@code true}
     */
    void save(SuiteRunStatus status);

    /**
     * Looks up a previously saved snapshot.
     *
     * @param runId the run identifier
     * @return the stored snapshot, or empty if the run is unknown or has been evicted
     */
    Optional<SuiteRunStatus> find(String runId);
}
