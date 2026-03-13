package no.kompilator.situs.domain;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Immutable descriptor for a single test case discovered during suite scanning.
 *
 * <p>Holds all metadata that {@link no.kompilator.situs.runtime.TestRunner}
 * needs to execute the test: the display name, optional description, the reflected
 * {@link java.lang.reflect.Method}, and the timeout/delay settings read from
 * {@code @Test}.
 *
 * <p>Instances are created by
 * {@link no.kompilator.situs.runtime.TestSuiteRegistry#getAllSuites(java.util.Set)}
 * and are read-only afterwards.
 */
public class TestCaseDefinition {

    private final String name;
    private final String description;
    private final Method method;
    private final long timeoutMs;
    private final long delayMs;
    private final int retries;
    private final Object[] arguments;

    /**
     * Creates a new test case descriptor.
     *
     * @param name        display name (from {@code @Test#name()}, or the method name as fallback)
     * @param description optional human-readable description of what the test verifies
     * @param method      the reflected method to invoke when the test runs
     * @param timeoutMs   maximum execution time in ms; {@code 0} = use framework default,
     *                    {@code -1} = no timeout
     * @param delayMs     milliseconds to wait before starting this test; {@code 0} = no delay
     * @param retries     number of additional attempts after an initial failure; {@code 0} = no retries
     */
    public TestCaseDefinition(String name, String description, Method method,
                              long timeoutMs, long delayMs, int retries) {
        this(name, description, method, timeoutMs, delayMs, retries, new Object[0]);
    }

    /**
     * Creates a new test case descriptor.
     *
     * @param name        display name for this concrete invocation
     * @param description optional human-readable description of what the test verifies
     * @param method      the reflected method to invoke when the test runs
     * @param timeoutMs   maximum execution time in ms; {@code 0} = use framework default,
     *                    {@code -1} = no timeout
     * @param delayMs     milliseconds to wait before starting this test; {@code 0} = no delay
     * @param retries     number of additional attempts after an initial failure; {@code 0} = no retries
     * @param arguments   invocation arguments for parameterized tests; empty for regular tests
     */
    public TestCaseDefinition(String name, String description, Method method,
                              long timeoutMs, long delayMs, int retries, Object[] arguments) {
        this.name = name;
        this.description = description;
        this.method = method;
        this.timeoutMs = timeoutMs;
        this.delayMs = delayMs;
        this.retries = retries;
        this.arguments = arguments == null ? new Object[0] : Arrays.copyOf(arguments, arguments.length);
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
     *         ({@link no.kompilator.situs.runtime.TestRunner#DEFAULT_TIMEOUT_MS}),
     *         {@code -1} to disable the timeout, or a positive value to use as-is
     */
    public long getTimeoutMs() { return timeoutMs; }

    /**
     * Returns the delay in milliseconds to wait before this test starts.
     *
     * @return {@code 0} for no delay, or a positive number of milliseconds
     */
    public long getDelayMs() { return delayMs; }

    /**
     * Returns the number of retry attempts after an initial failure.
     *
     * @return {@code 0} for no retries, or a positive number of additional attempts
     */
    public int getRetries() { return retries; }

    /** @return the invocation arguments for this test case; empty for regular tests */
    public Object[] getArguments() { return Arrays.copyOf(arguments, arguments.length); }
}
