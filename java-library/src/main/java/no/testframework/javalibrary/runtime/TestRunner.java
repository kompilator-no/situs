package no.testframework.javalibrary.runtime;

import no.testframework.javalibrary.annotations.AfterAll;
import no.testframework.javalibrary.annotations.AfterEach;
import no.testframework.javalibrary.annotations.BeforeAll;
import no.testframework.javalibrary.annotations.BeforeEach;
import no.testframework.javalibrary.annotations.RunTimeTest;
import no.testframework.javalibrary.domain.TestCaseExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TestRunner {

    private static final Logger log = LoggerFactory.getLogger(TestRunner.class);

    /** Default timeout applied when {@code @RunTimeTest(timeoutMs = 0)} (the default). */
    public static final long DEFAULT_TIMEOUT_MS = 10_000;

    public List<TestCaseExecutionResult> runTests(Class<?> testClass) {
        log.debug("Preparing tests in: {}", testClass.getSimpleName());

        List<Method> beforeAllMethods  = findMethods(testClass, BeforeAll.class);
        List<Method> beforeEachMethods = findMethods(testClass, BeforeEach.class);
        List<Method> afterEachMethods  = findMethods(testClass, AfterEach.class);
        List<Method> afterAllMethods   = findMethods(testClass, AfterAll.class);
        List<Method> testMethods       = findMethods(testClass, RunTimeTest.class);

        // @BeforeAll — invoked once on a shared instance before all tests
        Object suiteInstance = newInstance(testClass);
        invokeLifecycleMethods(beforeAllMethods, suiteInstance, "@BeforeAll");

        List<TestCaseExecutionResult> results = new ArrayList<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            for (Method method : testMethods) {
                RunTimeTest annotation = method.getAnnotation(RunTimeTest.class);
                String testName = annotation.name().isEmpty() ? method.getName() : annotation.name();

                // Resolve effective timeout:
                //   0  → use DEFAULT_TIMEOUT_MS (10 s)
                //  -1  → no timeout at all
                //  >0  → use the value as-is
                long configuredTimeout = annotation.timeoutMs();
                long effectiveTimeout = configuredTimeout == 0 ? DEFAULT_TIMEOUT_MS
                                      : configuredTimeout < 0 ? -1
                                      : configuredTimeout;

                long delayMs = annotation.delayMs();

                log.debug("Executing test: {} (timeout: {}, delay: {}ms)",
                        testName,
                        effectiveTimeout < 0 ? "none" : effectiveTimeout + "ms",
                        delayMs);

                // Optional pre-test delay
                if (delayMs > 0) {
                    log.debug("  Delaying test '{}' for {}ms", testName, delayMs);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        results.add(new TestCaseExecutionResult(testName, false, "Interrupted during delay", 0));
                        continue;
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
                        Throwable cause = t.getCause() != null ? t.getCause() : t;
                        throw new RuntimeException(cause.getMessage(), cause);
                    }
                });

                try {
                    if (effectiveTimeout > 0) {
                        future.get(effectiveTimeout, TimeUnit.MILLISECONDS);
                    } else {
                        future.get();
                    }
                    long duration = System.currentTimeMillis() - start;
                    log.debug("  [PASSED] {} ({}ms)", testName, duration);
                    results.add(new TestCaseExecutionResult(testName, true, null, duration));
                } catch (TimeoutException e) {
                    future.cancel(true);
                    long duration = System.currentTimeMillis() - start;
                    String msg = "Test timed out after " + effectiveTimeout + "ms";
                    log.debug("  [FAILED] {} ({}ms) - {}", testName, duration, msg);
                    results.add(new TestCaseExecutionResult(testName, false, msg, duration));
                } catch (Throwable t) {
                    long duration = System.currentTimeMillis() - start;
                    Throwable cause = t.getCause() != null ? t.getCause() : t;
                    log.debug("  [FAILED] {} ({}ms) - {}", testName, duration, cause.getMessage());
                    results.add(new TestCaseExecutionResult(testName, false, cause.getMessage(), duration));
                } finally {
                    invokeLifecycleMethods(afterEachMethods, instance, "@AfterEach");
                }
            }
        } finally {
            executor.shutdownNow();
        }

        // @AfterAll — invoked once on the shared instance after all tests
        invokeLifecycleMethods(afterAllMethods, suiteInstance, "@AfterAll");

        return results;
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
}

