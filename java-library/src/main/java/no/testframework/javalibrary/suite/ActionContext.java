package no.testframework.javalibrary.suite;

public record ActionContext(Registry<String, Object> registry, int iteration) {
}
