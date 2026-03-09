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
 * @Test(name = "addsTwoNumbers", description = "2 + 3 should be 5", timeoutMs = 1_000)
 * public void testAddition() {
 *     assertThat(calculator.add(2, 3)).isEqualTo(5);
 * }
 * }</pre>
 *
 * @see TestSuite
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Test {

    /**
     * Display name shown in reports and API responses.
     * Defaults to the method name if left blank.
     */
    String name() default "";

    /** Optional human-readable description of what this test verifies. */
    String description() default "";

    /**
     * Execution order within the suite.
     *
     * <p>Lower values run first. Tests with the same order are executed in method-name
     * order to keep execution deterministic across JVMs.
     */
    int order() default 0;

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

    /**
     * Number of times to retry the test after an initial failure before recording it as failed.
     *
     * <ul>
     *   <li>{@code 0} (default) — no retries; a single failure is recorded immediately.</li>
     *   <li>{@code n > 0}       — retry up to {@code n} additional times.
     *       The test passes as soon as one attempt succeeds.
     *       If all {@code n + 1} attempts fail, the <em>last</em> failure is recorded.</li>
     * </ul>
     *
     * <p>Each retry runs the full per-test lifecycle:
     * {@code @BeforeEach} → test body → {@code @AfterEach}.
     * The {@link no.testframework.javalibrary.domain.TestCaseExecutionResult#getAttempts()}
     * field in the result shows how many attempts were made.
     *
     * <p>Retries are useful for tests that interact with flaky external systems.
     * Do <b>not</b> use retries to mask real bugs in the code under test.
     */
    int retries() default 0;
}
