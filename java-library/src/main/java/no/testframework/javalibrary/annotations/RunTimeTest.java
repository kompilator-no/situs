package no.testframework.javalibrary.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a <b>runtime test</b> to be discovered and executed by the framework.
 *
 * <p>The annotated method must be {@code public} and have no parameters.
 * It signals a test failure by throwing any {@link Throwable} — use assertion libraries
 * such as AssertJ ({@code assertThat(...)}) or plain {@code throw new AssertionError(...)}.
 *
 * <p>Example:
 * <pre>{@code
 * @RunTimeTest(name = "addsTwoNumbers", description = "2 + 3 should be 5", timeoutMs = 1_000)
 * public void testAddition() {
 *     assertThat(calculator.add(2, 3)).isEqualTo(5);
 * }
 * }</pre>
 *
 * @see RuntimeTestSuite
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RunTimeTest {

    /**
     * Display name shown in reports and API responses.
     * Defaults to the method name if left blank.
     */
    String name() default "";

    /** Optional human-readable description of what this test verifies. */
    String description() default "";

    /**
     * Maximum allowed execution time in milliseconds.
     * <ul>
     *   <li>{@code 0}  (default) — use the framework default ({@code 10 000} ms)</li>
     *   <li>{@code -1}           — disable the timeout entirely (test waits indefinitely)</li>
     *   <li>{@code > 0}          — cancel the test after exactly this many milliseconds</li>
     * </ul>
     */
    long timeoutMs() default 0;

    /**
     * Milliseconds to wait before starting this test.
     * Useful for staggering tests or waiting for an external system to become ready.
     * {@code 0} (default) means start immediately.
     */
    long delayMs() default 0;
}
