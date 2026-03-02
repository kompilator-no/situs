package no.testframework.runner.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.testframework.runner.discovery.TestDefinitionRegistry;
import no.testframework.runner.execution.RunnerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public List<Map<String, String>> tests() {
        return registry.definitions().stream()
            .map(def -> Map.of("id", def.id(), "description", def.description()))
            .toList();
    }

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, UUID> start(@Valid @RequestBody RunRequest request) {
        UUID runId = runnerService.start(request.testId(), request.retries(), request.timeout(), request.context());
        return Map.of("runId", runId);
    }

    @DeleteMapping("/runs/{runId}")
    public Map<String, Object> stop(@PathVariable UUID runId) {
        boolean cancelled = runnerService.stop(runId);
        return Map.of("runId", runId, "cancelled", cancelled);
    }

    @GetMapping("/runs/{runId}")
    public RunResponse run(@PathVariable UUID runId) {
        return RunResponse.from(runnerService.require(runId));
    }

    @GetMapping("/runs")
    public List<RunResponse> runs() {
        return runnerService.all().values().stream().map(RunResponse::from).toList();
    }
}
