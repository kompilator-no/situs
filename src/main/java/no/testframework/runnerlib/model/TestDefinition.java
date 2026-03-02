package no.testframework.runnerlib.model;

import java.util.Map;

public interface TestDefinition {
    String id();

    String description();

    void execute(Map<String, Object> context) throws Exception;
}
