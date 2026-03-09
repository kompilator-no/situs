package no.kompilator.testframework.runtime;

import no.kompilator.testframework.annotations.AfterAll;
import no.kompilator.testframework.annotations.AfterEach;
import no.kompilator.testframework.annotations.BeforeAll;
import no.kompilator.testframework.annotations.BeforeEach;
import no.kompilator.testframework.annotations.Test;
import no.kompilator.testframework.domain.TestCaseExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
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
 *   <li>Discovering lifecycle methods ({@link no.kompilator.testframework.annotations.BeforeAll @BeforeAll},
 *       {@link no.kompilator.testframework.annotations.BeforeEach @BeforeEach},
 *       {@link no.kompilator.testframework.annotations.AfterEach @AfterEach},
 *       {@link no.kompilator.testframework.annotations.AfterAll @AfterAll}) and test methods
 *       ({@link no.kompilator.testframework.annotations.Test @Test})
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
 * @see no.kompilator.testframework.service.TestFrameworkService
 */
public class TestRunner {

    private static final Logger log = LoggerFactory.getLogger(TestRunner.class);

    /**
     * Default timeout applied when a test declares {@code timeoutMs = 0} (the annotation default).
     * Tests that run longer than this will be cancelled and recorded as failed.
     */
    public static final long DEFAULT_TIMEOUT_MS = 10_000;

    private final InstanceFactory instanceFactory;

    /**
     * Creates a {@code TestRunner} that instantiates suite classes via reflection
     * (no-arg constructor). Suitable for use without a DI container.
     */
    public TestRunner() {
        this(InstanceFactory.reflective());
    }

    /**
     * Creates a {@code TestRunner} that uses the supplied {@link InstanceFactory}
     * to create suite instances. Use this constructor to enable dependency injection
     * (e.g. pass a {@link no.kompilator.testframework.spring.SpringInstanceFactory}).
     *
     * @param instanceFactory the factory used to create suite class instances
     */
    public TestRunner(InstanceFactory instanceFactory) {
        this.instanceFactory = instanceFactory;
    }

    /**
     * Runs all {@code @Test} methods in {@code testClass} sequentially.
     * Convenience overload — equivalent to {@code runTests(testClass, false)}.
     *
     * @param testClass the suite class annotated with {@code @TestSuite}
     * @return an ordered list of results, one per test method
     */
    public List<TestCaseExecutionResult> runTests(Class<?> testClass) {
        return runTests(testClass, false, null);
    }

    /**
     * Runs a single named {@code @Test} method in {@code testClass}.
     *
     * <p>The suite-level lifecycle still applies:
     * <ol>
     *   <li>{@code @BeforeAll} once before the selected test</li>
     *   <li>{@code @BeforeEach} → selected test → {@code @AfterEach}</li>
     *   <li>{@code @AfterAll} once after the selected test</li>
     * </ol>
     *
     * @param testClass the suite class containing the test
     * @param testName  the logical test name from {@code @Test#name()} or method name fallback
     * @return the single test result
     * @throws IllegalArgumentException if the test name does not exist in the suite
     */
    public TestCaseExecutionResult runSingleTest(Class<?> testClass, String testName) {
        log.info("Running single test: {}.{}", testClass.getSimpleName(), testName);

        List<Method> beforeAllMethods  = findAnnotatedMethods(testClass, BeforeAll.class);
        List<Method> beforeEachMethods = findAnnotatedMethods(testClass, BeforeEach.class);
        List<Method> afterEachMethods  = findAnnotatedMethods(testClass, AfterEach.class);
        List<Method> afterAllMethods   = findAnnotatedMethods(testClass, AfterAll.class);
        List<Method> testMethods       = findAnnotatedMethods(testClass, Test.class);

        Method selectedMethod = testMethods.stream()
                .filter(method -> resolveTestName(method).equals(testName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Test not found: " + testName));

        Object suiteInstance = instanceFactory.createInstance(testClass);
        invokeLifecycleMethods(beforeAllMethods, suiteInstance, "@BeforeAll");

        try {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                return executeTestMethod(testClass, selectedMethod, beforeEachMethods, afterEachMethods, executor);
            } finally {
                executor.shutdownNow();
            }
        } finally {
            invokeLifecycleMethods(afterAllMethods, suiteInstance, "@AfterAll");
        }
    }

    /**
     * Runs all {@code @Test} methods in {@code testClass}.
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
     * @param testClass the suite class annotated with {@code @TestSuite}
     * @param parallel  {@code true} to run all tests concurrently;
     *                  {@code false} to run them one after another in declaration order
     * @return a list of results — in submission order for both modes
     */
    public List<TestCaseExecutionResult> runTests(Class<?> testClass, boolean parallel) {
        return runTests(testClass, parallel, null);
    }

    /**
     * Runs all {@code @Test} methods in {@code testClass} and optionally publishes
     * each completed test result as soon as it is available.
     *
     * @param testClass the suite class annotated with {@code @TestSuite}
     * @param parallel  {@code true} to run all tests concurrently
     * @param resultConsumer callback invoked once per completed test result, or {@code null}
     * @return a list of results — in submission order for both modes
     */
    public List<TestCaseExecutionResult> runTests(
            Class<?> testClass, boolean parallel, Consumer<TestCaseExecutionResult> resultConsumer) {
        log.info("Running suite: {} [{}]", testClass.getSimpleName(), parallel ? "PARALLEL" : "SEQUENTIAL");

        List<Method> beforeAllMethods  = findAnnotatedMethods(testClass, BeforeAll.class);
        List<Method> beforeEachMethods = findAnnotatedMethods(testClass, BeforeEach.class);
        List<Method> afterEachMethods  = findAnnotatedMethods(testClass, AfterEach.class);
        List<Method> afterAllMethods   = findAnnotatedMethods(testClass, AfterAll.class);
        List<Method> testMethods       = findAnnotatedMethods(testClass, Test.class);

        // @BeforeAll — invoked once on a shared instance before all tests
        Object suiteInstance = instanceFactory.createInstance(testClass);
        invokeLifecycleMethods(beforeAllMethods, suiteInstance, "@BeforeAll");

        List<TestCaseExecutionResult> results;
        if (parallel) {
            results = runParallel(testClass, testMethods, beforeEachMethods, afterEachMethods, resultConsumer);
        } else {
            results = runSequential(testClass, testMethods, beforeEachMethods, afterEachMethods, resultConsumer);
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
                                                         List<Method> afterEachMethods,
                                                         Consumer<TestCaseExecutionResult> resultConsumer) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            return testMethods.stream()
                    .map(method -> executeTestMethod(testClass, method, beforeEachMethods, afterEachMethods, executor))
                    .peek(result -> notifyResultConsumer(resultConsumer, result))
                    .toList();
        } finally {
            executor.shutdownNow();
        }
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
     *   <li>Results are materialized only after waiting on each submitted future.</li>
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
                                                       List<Method> afterEachMethods,
                                                       Consumer<TestCaseExecutionResult> resultConsumer) {
        if (testMethods.isEmpty()) {
            return List.of();
        }

        // Each test gets its own single-thread executor so its own timeout is enforced independently.
        ExecutorService pool = Executors.newFixedThreadPool(testMethods.size());
        List<Future<TestCaseExecutionResult>> futures = testMethods.stream()
                .map(method -> {
                    ExecutorService testExecutor = Executors.newSingleThreadExecutor();
                    return pool.submit(() -> {
                        try {
                            TestCaseExecutionResult result = executeTestMethod(
                                    testClass, method, beforeEachMethods, afterEachMethods, testExecutor);
                            notifyResultConsumer(resultConsumer, result);
                            return result;
                        } finally {
                            testExecutor.shutdownNow();
                        }
                    });
                })
                .toList();

        // Collect in submission order so the result list is deterministic.
        try {
            return futures.stream()
                    .map(this::collectFutureResult)
                    .toList();
        } finally {
            pool.shutdownNow();
        }
    }

    // -------------------------------------------------------------------------
    // Single-test execution (shared by both modes)
    // -------------------------------------------------------------------------

    /**
     * Executes a single test method with retry support and returns the final result.
     *
     * <p>The test is attempted up to {@code retries + 1} times. As soon as one attempt
     * passes the result is returned immediately. If every attempt fails, the result of
     * the <em>last</em> attempt is returned with {@code attempts} set to the total
     * number of tries.
     *
     * @param testClass         the suite class — a new instance is created for each attempt
     * @param method            the test method to invoke
     * @param beforeEachMethods lifecycle methods to run before each attempt
     * @param afterEachMethods  lifecycle methods to run after each attempt
     * @param executor          the executor to submit the test body to
     * @return the final {@link TestCaseExecutionResult} (pass on first success, last failure otherwise)
     */
    private TestCaseExecutionResult executeTestMethod(Class<?> testClass, Method method,
                                                      List<Method> beforeEachMethods,
                                                      List<Method> afterEachMethods,
                                                      ExecutorService executor) {
        Test annotation = method.getAnnotation(Test.class);
        int maxRetries = Math.max(0, annotation.retries());
        long totalStart = System.currentTimeMillis();

        TestCaseExecutionResult lastResult = null;
        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            if (attempt > 1) {
                log.info("  Retrying test '{}' (attempt {}/{})",
                        resolveTestName(method),
                        attempt, maxRetries + 1);
            }
            lastResult = runAttempt(testClass, method, beforeEachMethods, afterEachMethods,
                    executor, attempt, totalStart);
            if (lastResult.isPassed()) {
                return lastResult;
            }
        }
        return lastResult;
    }

    /**
     * Executes a single attempt of a test method.
     *
     * <p>Execution flow:
     * <ol>
     *   <li>On the first attempt only: apply optional {@code delayMs} pre-start delay.</li>
     *   <li>Create a fresh instance of the suite class.</li>
     *   <li>Invoke all {@code @BeforeEach} methods on that instance.</li>
     *   <li>Submit the test method to {@code executor} and wait with the effective timeout.</li>
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
     * @param testClass         the suite class
     * @param method            the test method to invoke
     * @param beforeEachMethods lifecycle methods to run before the test
     * @param afterEachMethods  lifecycle methods to run after the test
     * @param executor          the executor to submit the test body to
     * @param attempt           current attempt number (1-based), used for logging
     * @param totalStart        wall-clock start time of the first attempt, for cumulative duration
     * @return the {@link TestCaseExecutionResult} for this attempt
     */
    private TestCaseExecutionResult runAttempt(Class<?> testClass, Method method,
                                               List<Method> beforeEachMethods,
                                               List<Method> afterEachMethods,
                                               ExecutorService executor,
                                               int attempt, long totalStart) {
        Test annotation = method.getAnnotation(Test.class);
        String testName = resolveTestName(method);

        long configuredTimeout = annotation.timeoutMs();
        long effectiveTimeout = configuredTimeout == 0 ? DEFAULT_TIMEOUT_MS
                              : configuredTimeout <  0 ? -1
                              : configuredTimeout;
        long delayMs = annotation.delayMs();
        int maxRetries = Math.max(0, annotation.retries());

        log.debug("Executing test: {} (attempt: {}/{}, timeout: {}, delay: {}ms)",
                testName, attempt, maxRetries + 1,
                effectiveTimeout < 0 ? "none" : effectiveTimeout + "ms",
                attempt == 1 ? delayMs : 0);

        // delayMs only applies on the first attempt
        if (attempt == 1 && delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                long completedAt = System.currentTimeMillis();
                return new TestCaseExecutionResult(testName, false, "Interrupted during delay",
                        InterruptedException.class.getName(), null,
                        completedAt - totalStart, attempt, totalStart, completedAt);
            }
        }

        Object instance = instanceFactory.createInstance(testClass);
        invokeLifecycleMethods(beforeEachMethods, instance, "@BeforeEach");

        Future<?> future = executor.submit(() -> {
            try {
                method.setAccessible(true);
                method.invoke(instance);
            } catch (ReflectiveOperationException e) {
                Throwable cause = e instanceof InvocationTargetException invocationTargetException
                        && invocationTargetException.getCause() != null
                        ? invocationTargetException.getCause()
                        : e;
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
            long completedAt = System.currentTimeMillis();
            long duration = completedAt - totalStart;
            log.debug("  [PASSED] {} ({}ms, attempt {})", testName, duration, attempt);
            result = new TestCaseExecutionResult(testName, true, null, null, null, duration, attempt,
                    totalStart, completedAt);
        } catch (TimeoutException e) {
            future.cancel(true);
            long completedAt = System.currentTimeMillis();
            long duration = completedAt - totalStart;
            String msg = "Test timed out after " + effectiveTimeout + "ms";
            log.debug("  [FAILED] {} ({}ms, attempt {}) - {}", testName, duration, attempt, msg);
            result = new TestCaseExecutionResult(testName, false, msg,
                    TimeoutException.class.getName(), null, duration, attempt, totalStart, completedAt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long completedAt = System.currentTimeMillis();
            long duration = completedAt - totalStart;
            String msg = "Interrupted while waiting for test completion";
            log.debug("  [FAILED] {} ({}ms, attempt {}) - {}", testName, duration, attempt, msg);
            result = new TestCaseExecutionResult(testName, false, msg,
                    InterruptedException.class.getName(), null, duration, attempt, totalStart, completedAt);
        } catch (ExecutionException e) {
            long completedAt = System.currentTimeMillis();
            long duration = completedAt - totalStart;
            Throwable root = e.getCause() == null ? e : e.getCause();
            while (root.getCause() != null) { root = root.getCause(); }
            log.debug("  [FAILED] {} ({}ms, attempt {}) - {}", testName, duration, attempt, root.getMessage());
            result = new TestCaseExecutionResult(testName, false, root.getMessage(),
                    root.getClass().getName(), stackTraceOf(root), duration, attempt, totalStart, completedAt);
        } finally {
            invokeLifecycleMethods(afterEachMethods, instance, "@AfterEach");
        }
        return result;
    }

    private String resolveTestName(Method method) {
        Test annotation = method.getAnnotation(Test.class);
        return annotation.name().isEmpty() ? method.getName() : annotation.name();
    }

    private TestCaseExecutionResult collectFutureResult(Future<TestCaseExecutionResult> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while collecting parallel test result", e);
        } catch (ExecutionException | CancellationException e) {
            throw new IllegalStateException("Unexpected error collecting parallel test result", e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns all methods on {@code clazz} that carry the given annotation.
     * Sets each method accessible so private/package-private methods can be invoked.
     *
     * @param clazz      the class to inspect
     * @param annotationType the annotation to look for
     * @return a mutable list of matching methods; empty if none found
     */
    private List<Method> findAnnotatedMethods(Class<?> clazz, Class<? extends Annotation> annotationType) {
        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(annotationType))
                .sorted(Comparator
                        .comparingInt((Method method) -> resolveOrder(method, annotationType))
                        .thenComparing(Method::getName))
                .peek(method -> method.setAccessible(true))
                .toList();
    }

    private int resolveOrder(Method method, Class<? extends Annotation> annotationType) {
        if (annotationType == Test.class) {
            return method.getAnnotation(Test.class).order();
        }
        if (annotationType == BeforeAll.class) {
            return method.getAnnotation(BeforeAll.class).order();
        }
        if (annotationType == BeforeEach.class) {
            return method.getAnnotation(BeforeEach.class).order();
        }
        if (annotationType == AfterEach.class) {
            return method.getAnnotation(AfterEach.class).order();
        }
        if (annotationType == AfterAll.class) {
            return method.getAnnotation(AfterAll.class).order();
        }
        return 0;
    }

    private void notifyResultConsumer(Consumer<TestCaseExecutionResult> resultConsumer, TestCaseExecutionResult result) {
        if (resultConsumer != null) {
            resultConsumer.accept(result);
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
            } catch (IllegalAccessException | InvocationTargetException e) {
                Throwable cause = e instanceof InvocationTargetException invocationTargetException
                        && invocationTargetException.getCause() != null
                        ? invocationTargetException.getCause()
                        : e;
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
