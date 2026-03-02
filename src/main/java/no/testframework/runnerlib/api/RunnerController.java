package no.testframework.runnerlib.api;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.testframework.runnerlib.discovery.TestDefinitionRegistry;
import no.testframework.runnerlib.execution.RunState;
import no.testframework.runnerlib.execution.RunSummary;
import no.testframework.runnerlib.execution.RunnerService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RunnerController {

    private final RunnerService runnerService;
    private final TestDefinitionRegistry registry;

    public RunnerController(RunnerService runnerService, TestDefinitionRegistry registry) {
        this.runnerService = runnerService;
        this.registry = registry;
    }

    @GetMapping("/tests")
    @PreAuthorize("hasAuthority('runner:read')")
    public List<Map<String, String>> tests() {
        return registry.definitions().stream()
            .map(def -> Map.of("id", def.id(), "description", def.description()))
            .toList();
    }

    @PostMapping("/runs")
    @PreAuthorize("hasAuthority('runner:execute')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, UUID> start(@Valid @RequestBody RunRequest request,
                                   @RequestHeader(value = "X-Correlation-Id", required = false) String headerCorrelationId,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                   @RequestHeader HttpHeaders headers) {
        Map<String, Object> context = request.context() != null ? new HashMap<>(request.context()) : new HashMap<>();
        String correlationId = headerCorrelationId != null ? headerCorrelationId : UUID.randomUUID().toString();
        SpanContext spanContext = Span.current().getSpanContext();
        String traceId = spanContext.isValid() ? spanContext.getTraceId() : "";
        String traceparent = headers.getFirst("traceparent");

        context.put("correlationId", correlationId);
        context.put("traceId", traceId);
        if (traceparent != null && !traceparent.isBlank()) {
            context.put("traceparent", traceparent);
        }

        UUID runId = runnerService.start(request.testId(), request.retries(), request.timeout(), context, idempotencyKey);
        return Map.of("runId", runId);
    }

    @DeleteMapping("/runs/{runId}")
    @PreAuthorize("hasAuthority('runner:cancel')")
    public Map<String, Object> stop(@PathVariable UUID runId) {
        boolean cancelled = runnerService.stop(runId);
        return Map.of("runId", runId, "cancelled", cancelled);
    }

    @GetMapping("/runs/{runId}")
    @PreAuthorize("hasAuthority('runner:read')")
    public RunResponse run(@PathVariable UUID runId) {
        return RunResponse.from(runnerService.require(runId));
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAuthority('runner:read')")
    public List<RunResponse> runs(@RequestParam(required = false) String testId,
                                  @RequestParam(required = false) RunState state,
                                  @RequestParam(defaultValue = "50") int limit,
                                  @RequestParam(defaultValue = "0") int offset) {
        int normalizedLimit = Math.min(Math.max(limit, 1), 500);
        int normalizedOffset = Math.max(offset, 0);
        return runnerService.all(testId, state, normalizedLimit, normalizedOffset)
            .stream()
            .map(RunResponse::from)
            .toList();
    }

    @GetMapping("/runs/summary")
    @PreAuthorize("hasAuthority('runner:read')")
    public RunSummary summary() {
        return runnerService.summary();
    }
}
