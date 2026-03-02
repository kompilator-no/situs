package no.testframework.runnerlib.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class RunRecord {
    private final UUID runId;
    private final String testId;
    private final int maxRetries;
    private final Duration timeout;
    private final Map<String, Object> context;
    private final String correlationId;
    private final String traceId;
    private final String idempotencyKey;
    private final String idempotencyFingerprint;
    private final Instant createdAt = Instant.now();
    private final AtomicInteger attempts = new AtomicInteger(0);
    private final AtomicReference<RunState> state = new AtomicReference<>(RunState.QUEUED);
    private volatile String detail;
    private volatile String errorType;
    private volatile Instant startedAt;
    private volatile Instant finishedAt;

    public RunRecord(UUID runId,
                     String testId,
                     int maxRetries,
                     Duration timeout,
                     Map<String, Object> context,
                     String correlationId,
                     String traceId,
                     String idempotencyKey,
                     String idempotencyFingerprint) {
        this.runId = runId;
        this.testId = testId;
        this.maxRetries = maxRetries;
        this.timeout = timeout;
        this.context = context;
        this.correlationId = correlationId;
        this.traceId = traceId;
        this.idempotencyKey = idempotencyKey;
        this.idempotencyFingerprint = idempotencyFingerprint;
    }

    public UUID getRunId() { return runId; }
    public String getTestId() { return testId; }
    public int getMaxRetries() { return maxRetries; }
    public Duration getTimeout() { return timeout; }
    public Map<String, Object> getContext() { return context; }
    public String getCorrelationId() { return correlationId; }
    public String getTraceId() { return traceId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getIdempotencyFingerprint() { return idempotencyFingerprint; }
    public Instant getCreatedAt() { return createdAt; }
    public int incrementAttempts() { return attempts.incrementAndGet(); }
    public int getAttempts() { return attempts.get(); }
    public RunState getState() { return state.get(); }
    public void setState(RunState newState) { this.state.set(newState); }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public Long durationMs() {
        if (startedAt == null || finishedAt == null) {
            return null;
        }
        return Duration.between(startedAt, finishedAt).toMillis();
    }
}
