package no.testframework.javalibrary.suite;

public record Assertion(boolean passed, String message) {
    public static Assertion success() {
        return new Assertion(true, "");
    }

    public static Assertion failed() {
        return new Assertion(false, "Validation failed");
    }

    public static Assertion failed(String message) {
        return new Assertion(false, message == null ? "Validation failed" : message);
    }
}
