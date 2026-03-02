package no.testframework.runnerlib.execution;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class RunRecord {
    private final UUID runId;
    private final String testId;
    private final int maxRetries;
    private final java.time.Duration timeout;
    private final Map<String, Object> context;
    private final Instant createdAt = Instant.now();
    private final AtomicInteger attempts = new AtomicInteger(0);
    private final AtomicReference<RunState> state = new AtomicReference<>(RunState.QUEUED);
    private volatile String detail;
    private volatile Instant startedAt;
    private volatile Instant finishedAt;

    public RunRecord(UUID runId, String testId, int maxRetries, java.time.Duration timeout, Map<String, Object> context) {
        this.runId = runId;
        this.testId = testId;
        this.maxRetries = maxRetries;
        this.timeout = timeout;
        this.context = context;
    }

    public UUID getRunId() { return runId; }
    public String getTestId() { return testId; }
    public int getMaxRetries() { return maxRetries; }
    public java.time.Duration getTimeout() { return timeout; }
    public Map<String, Object> getContext() { return context; }
    public Instant getCreatedAt() { return createdAt; }
    public int incrementAttempts() { return attempts.incrementAndGet(); }
    public int getAttempts() { return attempts.get(); }
    public RunState getState() { return state.get(); }
    public void setState(RunState newState) { this.state.set(newState); }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
