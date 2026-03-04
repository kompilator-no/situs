package no.testframework.javalibrary.suite;

public record Iterations(int value) {
    public Iterations {
        if (value <= 0) {
            throw new IllegalArgumentException("iterations must be positive");
        }
    }

    public static Iterations from(int value) {
        return new Iterations(value);
    }
}
