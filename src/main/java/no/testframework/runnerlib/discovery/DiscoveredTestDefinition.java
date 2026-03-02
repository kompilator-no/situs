package no.testframework.runnerlib.discovery;

public record DiscoveredTestDefinition(String id, String description, Class<?> implementationClass) {
}
