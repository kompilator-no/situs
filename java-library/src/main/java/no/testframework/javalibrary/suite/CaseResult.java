package no.testframework.javalibrary.suite;

import no.testframework.javalibrary.runtime.TestStatus;

import java.util.List;

public record CaseResult(
        String name,
        String description,
        TestStatus status,
        List<StepResult> stepResults
) {
    public CaseResult {
        stepResults = List.copyOf(stepResults);
    }
}
