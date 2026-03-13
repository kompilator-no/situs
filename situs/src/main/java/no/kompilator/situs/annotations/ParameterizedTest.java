package no.kompilator.situs.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a parameterized runtime test.
 *
 * <p>The method must be {@code public}, non-static, and declare at least one parameter.
 * Arguments are supplied by one or more source annotations such as {@link ValueSource},
 * {@link CsvSource}, {@link MethodSource}, or {@link EnumSource}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ParameterizedTest {

    /**
     * Display name template for each generated invocation.
     *
     * <p>Supported placeholders:
     * <ul>
     *   <li>{@code {index}}: 1-based invocation index</li>
     *   <li>{@code {arguments}}: comma-separated rendered argument list</li>
     *   <li>{@code {0}}, {@code {1}}, ...: individual argument values</li>
     * </ul>
     *
     * <p>If blank, the framework uses the method name followed by {@code [index]}.
     */
    String name() default "";

    /** Optional human-readable description of what this test verifies. */
    String description() default "";

    /** Execution order within the suite. Lower values run first. */
    int order() default 0;

    /** Timeout behavior matches {@link Test#timeoutMs()}. */
    long timeoutMs() default 0;

    /** Timeout behavior matches {@link Test#timeout()}. */
    String timeout() default "";

    /** Delay behavior matches {@link Test#delayMs()}. */
    long delayMs() default 0;

    /** Retry behavior matches {@link Test#retries()}. */
    int retries() default 0;
}
