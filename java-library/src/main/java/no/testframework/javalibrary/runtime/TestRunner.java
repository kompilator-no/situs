package no.testframework.javalibrary.runtime;

import no.testframework.javalibrary.annotations.AfterAll;
import no.testframework.javalibrary.annotations.AfterEach;
import no.testframework.javalibrary.annotations.BeforeAll;
import no.testframework.javalibrary.annotations.BeforeEach;
import no.testframework.javalibrary.annotations.RunTimeTest;
import no.testframework.javalibrary.domain.TestCaseExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Core test execution engine for the runtime test framework.
 *
 * <p>Responsible for:
 * <ul>
 *   <li>Discovering lifecycle methods ({@link no.testframework.javalibrary.annotations.BeforeAll @BeforeAll},
 *       {@link no.testframework.javalibrary.annotations.BeforeEach @BeforeEach},
 *       {@link no.testframework.javalibrary.annotations.AfterEach @AfterEach},
 *       {@link no.testframework.javalibrary.annotations.AfterAll @AfterAll}) and test methods
 *       ({@link no.testframework.javalibrary.annotations.RunTimeTest @RunTimeTest})
 *       on a suite class via reflection.</li>
 *   <li>Running tests either <b>sequentially</b> (one after another, in declaration order)
 *       or <b>in parallel</b> (all tests submitted concurrently to a thread pool).</li>
 *   <li>Enforcing per-test timeouts — cancelling the test thread when the limit is exceeded.</li>
 *   <li>Capturing the full exception chain (type, message, stack trace) on failure.</li>
 * </ul>
 *
 * <p>Thread safety: a single {@code TestRunner} instance is safe to call from multiple
 * threads because it holds no mutable state — all state lives in local variables per call.
 *
 * @see RuntimeTestSuiteRunner
 * @see no.testframework.javalibrary.spring.TestFrameworkService
 */
public class TestRunner {

    private static final Logger log = LoggerFactory.getLogger(TestRunner.class);

    /**
     * Default timeout applied when a test declares {@code timeoutMs = 0} (the annotation default).
     * Tests that run longer than this will be cancelled and recorded as failed.
     */
    public static final long DEFAULT_TIMEOUT_MS = 10_000;

    /**
     * Runs all {@code @RunTimeTest} methods in {@code testClass} sequentially.
     * Convenience overload — equivalent to {@code runTests(testClass, false)}.
     *
     * @param testClass the suite class annotated with {@code @RuntimeTestSuite}
     * @return an ordered list of results, one per test method
     */
    public List<TestCaseExecutionResult> runTests(Class<?> testClass) {
        return runTests(testClass, false);
    }

    /**
     * Runs all {@code @RunTimeTest} methods in {@code testClass}.
     *
     * <p>Lifecycle order (regardless of parallel/sequential mode):
     * <ol>
     *   <li>{@code @BeforeAll} — once, on a shared instance, before any test starts</li>
     *   <li>For each test: {@code @BeforeEach} → test body → {@code @AfterEach}</li>
     *   <li>{@code @AfterAll} — once, on the shared instance, after all tests finish</li>
     * </ol>
     *
     * <p>In <b>parallel</b> mode each test runs in its own thread with its own object
     * instance, so tests must not share mutable state unless that state is thread-safe.
     *
     * @param testClass the suite class annotated with {@code @RuntimeTestSuite}
     * @param parallel  {@code true} to run all tests concurrently;
     *                  {@code false} to run them one after another in declaration order
     * @return a list of results — in submission order for both modes
     */
    public List<TestCaseExecutionResult> runTests(Class<?> testClass, boolean parallel) {
        log.info("Running suite: {} [{}]", testClass.getSimpleName(), parallel ? "PARALLEL" : "SEQUENTIAL");

        List<Method> beforeAllMethods  = findMethods(testClass, BeforeAll.class);
        List<Method> beforeEachMethods = findMethods(testClass, BeforeEach.class);
        List<Method> afterEachMethods  = findMethods(testClass, AfterEach.class);
        List<Method> afterAllMethods   = findMethods(testClass, AfterAll.class);
        List<Method> testMethods       = findMethods(testClass, RunTimeTest.class);

        // @BeforeAll — invoked once on a shared instance before all tests
        Object suiteInstance = newInstance(testClass);
        invokeLifecycleMethods(beforeAllMethods, suiteInstance, "@BeforeAll");

        List<TestCaseExecutionResult> results;
        if (parallel) {
            results = runParallel(testClass, testMethods, beforeEachMethods, afterEachMethods);
        } else {
            results = runSequential(testClass, testMethods, beforeEachMethods, afterEachMethods);
        }

        // @AfterAll — invoked once on the shared instance after all tests
        invokeLifecycleMethods(afterAllMethods, suiteInstance, "@AfterAll");

        return results;
    }

    // -------------------------------------------------------------------------
    // Sequential execution
    // -------------------------------------------------------------------------

    /**
     * Runs {@code testMethods} one by one on a single reused {@link ExecutorService}.
     * Using an executor even in sequential mode keeps the timeout logic consistent with
     * parallel mode — the calling thread blocks on {@code future.get(timeout, ...)} while
     * the test runs on the executor thread.
     *
     * @param testClass        the suite class (used to create fresh instances)
     * @param testMethods      methods to execute, in iteration order
     * @param beforeEachMethods lifecycle methods to call before each test
     * @param afterEachMethods  lifecycle methods to call after each test
     * @return results in the same order as {@code testMethods}
     */
    private List<TestCaseExecutionResult> runSequential(Class<?> testClass, List<Method> testMethods,
                                                         List<Method> beforeEachMethods,
                                                         List<Method> afterEachMethods) {
        List<TestCaseExecutionResult> results = new ArrayList<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            for (Method method : testMethods) {
                results.add(runSingleTest(testClass, method, beforeEachMethods, afterEachMethods, executor));
            }
        } finally {
            executor.shutdownNow();
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // Parallel execution
    // -------------------------------------------------------------------------

    /**
     * Submits all {@code testMethods} to a fixed thread pool simultaneously.
     *
     * <p>Design notes:
     * <ul>
     *   <li>Each test gets its <em>own</em> single-thread {@link ExecutorService} so that
     *       timeouts are enforced independently — one timed-out test does not affect others.</li>
     *   <li>Results are collected via {@code Future.get()} in submission order, so the
     *       returned list is deterministic even though execution was concurrent.</li>
     *   <li>A {@link java.util.concurrent.CopyOnWriteArrayList} is used for the result list
     *       because multiple threads add to it concurrently.</li>
     * </ul>
     *
     * @param testClass        the suite class (used to create fresh instances per test)
     * @param testMethods      methods to execute concurrently
     * @param beforeEachMethods lifecycle methods to call before each test (on that test's instance)
     * @param afterEachMethods  lifecycle methods to call after each test (on that test's instance)
     * @return results in submission order
     */
    private List<TestCaseExecutionResult> runParallel(Class<?> testClass, List<Method> testMethods,
                                                       List<Method> beforeEachMethods,
                                                       List<Method> afterEachMethods) {
        // Each test gets its own single-thread executor so its own timeout is enforced independently.
        ExecutorService pool = Executors.newFixedThreadPool(testMethods.size());
        List<Future<TestCaseExecutionResult>> futures = new ArrayList<>();

        for (Method method : testMethods) {
            ExecutorService testExecutor = Executors.newSingleThreadExecutor();
            futures.add(pool.submit(() ->
                    runSingleTest(testClass, method, beforeEachMethods, afterEachMethods, testExecutor)));
        }

        // Collect in submission order so the result list is deterministic.
        List<TestCaseExecutionResult> results = new CopyOnWriteArrayList<>();
        for (Future<TestCaseExecutionResult> f : futures) {
            try {
                results.add(f.get());
            } catch (Exception e) {
                log.error("Unexpected error collecting parallel test result", e);
            }
        }
        pool.shutdownNow();
        return Collections.unmodifiableList(results);
    }

    // -------------------------------------------------------------------------
    // Single-test execution (shared by both modes)
    // -------------------------------------------------------------------------

    /**
     * Executes a single test method and returns its result.
     *
     * <p>Execution flow:
     * <ol>
     *   <li>Apply optional {@code delayMs} pre-start delay.</li>
     *   <li>Create a fresh instance of the suite class.</li>
     *   <li>Invoke all {@code @BeforeEach} methods on that instance.</li>
     *   <li>Submit the test method to {@code executor} and wait for it with the
     *       effective timeout.</li>
     *   <li>Invoke all {@code @AfterEach} methods — always, even on failure or timeout.</li>
     * </ol>
     *
     * <p>Timeout resolution:
     * <ul>
     *   <li>{@code timeoutMs == 0}  → use {@link #DEFAULT_TIMEOUT_MS}</li>
     *   <li>{@code timeoutMs == -1} → no timeout (waits indefinitely)</li>
     *   <li>{@code timeoutMs > 0}   → use that value exactly</li>
     * </ul>
     *
     * <p>Exception unwrapping: {@code future.get()} wraps exceptions as
     * {@code ExecutionException → RuntimeException → original cause}.
     * This method walks the full cause chain to record the root exception type,
     * message, and stack trace.
     *
     * @param testClass         the suite class — a new instance is created for each call
     * @param method            the test method to invoke
     * @param beforeEachMethods lifecycle methods to run before the test
     * @param afterEachMethods  lifecycle methods to run after the test
     * @param executor          the executor to submit the test body to
     * @return a {@link no.testframework.javalibrary.domain.TestCaseExecutionResult} describing pass/fail, duration, and error details
     */
    private TestCaseExecutionResult runSingleTest(Class<?> testClass, Method method,
                                                   List<Method> beforeEachMethods,
                                                   List<Method> afterEachMethods,
                                                   ExecutorService executor) {
        RunTimeTest annotation = method.getAnnotation(RunTimeTest.class);
        String testName = annotation.name().isEmpty() ? method.getName() : annotation.name();

        long configuredTimeout = annotation.timeoutMs();
        long effectiveTimeout = configuredTimeout == 0 ? DEFAULT_TIMEOUT_MS
                              : configuredTimeout <  0 ? -1
                              : configuredTimeout;
        long delayMs = annotation.delayMs();

        log.debug("Executing test: {} (timeout: {}, delay: {}ms)",
                testName,
                effectiveTimeout < 0 ? "none" : effectiveTimeout + "ms",
                delayMs);

        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new TestCaseExecutionResult(testName, false, "Interrupted during delay",
                        InterruptedException.class.getName(), null, 0);
            }
        }

        Object instance = newInstance(testClass);
        invokeLifecycleMethods(beforeEachMethods, instance, "@BeforeEach");

        long start = System.currentTimeMillis();
        Future<?> future = executor.submit(() -> {
            try {
                method.setAccessible(true);
                method.invoke(instance);
            } catch (Throwable t) {
                // Unwrap InvocationTargetException so the original exception propagates
                Throwable cause = t.getCause() != null ? t.getCause() : t;
                throw new RuntimeException(cause.getMessage(), cause);
            }
        });

        TestCaseExecutionResult result;
        try {
            if (effectiveTimeout > 0) {
                future.get(effectiveTimeout, TimeUnit.MILLISECONDS);
            } else {
                future.get();
            }
            long duration = System.currentTimeMillis() - start;
            log.debug("  [PASSED] {} ({}ms)", testName, duration);
            result = new TestCaseExecutionResult(testName, true, null, duration);
        } catch (TimeoutException e) {
            future.cancel(true);
            long duration = System.currentTimeMillis() - start;
            String msg = "Test timed out after " + effectiveTimeout + "ms";
            log.debug("  [FAILED] {} ({}ms) - {}", testName, duration, msg);
            result = new TestCaseExecutionResult(testName, false, msg,
                    TimeoutException.class.getName(), null, duration);
        } catch (Throwable t) {
            long duration = System.currentTimeMillis() - start;
            // future.get() wraps in ExecutionException → RuntimeException → original cause
            // keep unwrapping until we reach the root
            Throwable root = t;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            log.debug("  [FAILED] {} ({}ms) - {}", testName, duration, root.getMessage());
            result = new TestCaseExecutionResult(testName, false, root.getMessage(),
                    root.getClass().getName(), stackTraceOf(root), duration);
        } finally {
            invokeLifecycleMethods(afterEachMethods, instance, "@AfterEach");
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns all methods on {@code clazz} that carry the given annotation.
     * Sets each method accessible so private/package-private methods can be invoked.
     *
     * @param clazz      the class to inspect
     * @param annotation the annotation to look for
     * @return a mutable list of matching methods; empty if none found
     */
    private List<Method> findMethods(Class<?> clazz, Class<? extends java.lang.annotation.Annotation> annotation) {
        List<Method> methods = new ArrayList<>();
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.isAnnotationPresent(annotation)) {
                m.setAccessible(true);
                methods.add(m);
            }
        }
        return methods;
    }

    /**
     * Creates a new instance of {@code clazz} using its no-arg constructor.
     *
     * @param clazz the class to instantiate
     * @return a fresh instance
     * @throws RuntimeException if the class has no accessible no-arg constructor or instantiation fails
     */
    private Object newInstance(Class<?> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate " + clazz.getName(), e);
        }
    }

    /**
     * Invokes each method in {@code methods} on {@code instance} in list order.
     * Unwraps {@link java.lang.reflect.InvocationTargetException} so the original
     * exception message is preserved in the wrapping {@link RuntimeException}.
     *
     * @param methods  the lifecycle methods to invoke
     * @param instance the object to invoke them on
     * @param phase    a label used in the error message (e.g. {@code "@BeforeEach"})
     * @throws RuntimeException if any lifecycle method throws
     */
    private void invokeLifecycleMethods(List<Method> methods, Object instance, String phase) {
        for (Method m : methods) {
            try {
                log.debug("  Invoking {} method: {}", phase, m.getName());
                m.invoke(instance);
            } catch (Throwable t) {
                Throwable cause = t.getCause() != null ? t.getCause() : t;
                throw new RuntimeException(phase + " method '" + m.getName() + "' failed: " + cause.getMessage(), cause);
            }
        }
    }

    /**
     * Converts the full stack trace of {@code t} to a {@link String}.
     * Used to capture the stack trace of a failed test for inclusion in the API response.
     *
     * @param t the throwable whose stack trace to capture
     * @return the stack trace as a multi-line string
     */
    private static String stackTraceOf(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
