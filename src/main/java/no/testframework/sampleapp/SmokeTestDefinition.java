package no.testframework.sampleapp;

import java.util.Map;
import no.testframework.runnerlib.discovery.TestDefinitionComponent;
import no.testframework.runnerlib.model.TestDefinition;
import org.springframework.stereotype.Component;

@Component
@TestDefinitionComponent(id = "smoke-test", description = "Simple smoke verification")
public class SmokeTestDefinition implements TestDefinition {
    @Override
    public String id() {
        return "smoke-test";
    }

    @Override
    public String description() {
        return "Simple smoke verification";
    }

    @Override
    public void execute(Map<String, Object> context) throws Exception {
        Object delay = context.getOrDefault("delayMs", 50);
        Thread.sleep(Long.parseLong(delay.toString()));
    }
}
