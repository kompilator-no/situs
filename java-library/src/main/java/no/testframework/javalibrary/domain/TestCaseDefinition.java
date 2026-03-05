package no.testframework.javalibrary.domain;

import java.lang.reflect.Method;

/**
 * Immutable descriptor for a single test case discovered during suite scanning.
 *
 * <p>Holds all metadata that {@link no.testframework.javalibrary.runtime.TestRunner}
 * needs to execute the test: the display name, optional description, the reflected
 * {@link java.lang.reflect.Method}, and the timeout/delay settings read from
 * {@code @RunTimeTest}.
 *
 * <p>Instances are created by
 * {@link no.testframework.javalibrary.runtime.TestSuiteRegistry#getAllSuites(java.util.Set)}
 * and are read-only afterwards.
 */
public class TestCaseDefinition {

    private final String name;
    private final String description;
    private final Method method;
    private final long timeoutMs;
    private final long delayMs;

    /**
     * Creates a new test case descriptor.
     *
     * @param name        display name (from {@code @RunTimeTest#name()}, or the method name as fallback)
     * @param description optional human-readable description of what the test verifies
     * @param method      the reflected method to invoke when the test runs
     * @param timeoutMs   maximum execution time in ms; {@code 0} = use framework default,
     *                    {@code -1} = no timeout
     * @param delayMs     milliseconds to wait before starting this test; {@code 0} = no delay
     */
    public TestCaseDefinition(String name, String description, Method method, long timeoutMs, long delayMs) {
        this.name = name;
        this.description = description;
        this.method = method;
        this.timeoutMs = timeoutMs;
        this.delayMs = delayMs;
    }

    /** @return the display name of this test case */
    public String getName() { return name; }

    /** @return the optional description, may be empty but never {@code null} */
    public String getDescription() { return description; }

    /** @return the reflected method to invoke */
    public Method getMethod() { return method; }

    /**
     * Returns the configured timeout in milliseconds.
     *
     * @return {@code 0} to use the framework default
     *         ({@link no.testframework.javalibrary.runtime.TestRunner#DEFAULT_TIMEOUT_MS}),
     *         {@code -1} to disable the timeout, or a positive value to use as-is
     */
    public long getTimeoutMs() { return timeoutMs; }

    /**
     * Returns the delay in milliseconds to wait before this test starts.
     *
     * @return {@code 0} for no delay, or a positive number of milliseconds
     */
    public long getDelayMs() { return delayMs; }
}
