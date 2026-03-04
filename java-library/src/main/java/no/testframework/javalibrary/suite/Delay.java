package no.testframework.javalibrary.suite;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public record Delay(long value, TimeUnit unit) {
    public Delay {
        if (value < 0) {
            throw new IllegalArgumentException("delay cannot be negative");
        }
        unit = Objects.requireNonNull(unit, "unit cannot be null");
    }

    public static Delay from(long value, TimeUnit unit) {
        return new Delay(value, unit);
    }

    public static Delay none() {
        return from(0, TimeUnit.MILLISECONDS);
    }

    void sleepIfNeeded() throws InterruptedException {
        if (value > 0) {
            unit.sleep(value);
        }
    }
}
