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

public class TestRunner {

    private static final Logger log = LoggerFactory.getLogger(TestRunner.class);

    /** Default timeout applied when {@code @RunTimeTest(timeoutMs = 0)} (the default). */
    public static final long DEFAULT_TIMEOUT_MS = 10_000;

    /** Runs all tests sequentially (default). */
    public List<TestCaseExecutionResult> runTests(Class<?> testClass) {
        return runTests(testClass, false);
    }

    /**
     * Runs all tests in the suite.
     *
     * @param parallel {@code true}  — each test runs in its own thread concurrently.
     *                 {@code false} — tests run one after another in declaration order.
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

    private Object newInstance(Class<?> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate " + clazz.getName(), e);
        }
    }

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

    private static String stackTraceOf(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
