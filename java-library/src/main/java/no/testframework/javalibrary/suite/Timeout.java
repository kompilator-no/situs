package no.testframework.javalibrary.suite;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public record Timeout(long value, TimeUnit unit) {
    public Timeout {
        if (value <= 0) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        unit = Objects.requireNonNull(unit, "unit cannot be null");
    }

    public static Timeout from(long value, TimeUnit unit) {
        return new Timeout(value, unit);
    }

    public static Timeout defaultTimeout() {
        return from(1, TimeUnit.MINUTES);
    }
}
