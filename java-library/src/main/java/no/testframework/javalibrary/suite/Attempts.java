package no.testframework.javalibrary.suite;

public record Attempts(int value, Delay delay) {
    public Attempts {
        if (value <= 0) {
            throw new IllegalArgumentException("attempts must be positive");
        }
        delay = delay == null ? Delay.none() : delay;
    }

    public static Attempts from(int value) {
        return new Attempts(value, Delay.none());
    }

    public static Attempts from(int value, Delay delay) {
        return new Attempts(value, delay);
    }
}
