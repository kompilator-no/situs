package no.testframework.runner.discovery;

public record DiscoveredTestDefinition(String id, String description, Class<?> implementationClass) {
}
