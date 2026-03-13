package no.kompilator.situs.params;

import java.util.Arrays;

/**
 * Represents one argument set emitted by a {@code @MethodSource} provider.
 */
public final class Arguments {

    private final Object[] values;

    private Arguments(Object[] values) {
        this.values = values;
    }

    public static Arguments of(Object... values) {
        return new Arguments(values == null ? new Object[]{null} : Arrays.copyOf(values, values.length));
    }

    public Object[] values() {
        return Arrays.copyOf(values, values.length);
    }
}
