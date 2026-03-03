package no.testframework.javalibrary.suite;

import java.util.Objects;

public record Report(String message) {
    public Report {
        message = Objects.requireNonNullElse(message, "");
    }

    public static Report from(String message) {
        return new Report(message);
    }
}
