package no.testframework.javalibrary.suite;

import java.util.List;
import java.util.Map;

public record SuiteResult(
        String name,
        String description,
        TestStatus status,
        List<CaseResult> testCaseResults,
        Map<String, Object> contextSnapshot
) {
    public SuiteResult {
        testCaseResults = List.copyOf(testCaseResults);
        contextSnapshot = Map.copyOf(contextSnapshot);
    }
}
