package no.testframework.javalibrary.plugin;

import no.testframework.javalibrary.domain.TestSuiteExecutionResult;

/**
 * Callback interface notified whenever a test suite run completes.
 *
 * <p>Register implementations with
 * {@link no.testframework.javalibrary.spring.TestFrameworkService#addListener(SuiteRunListener)}
 * to receive results automatically after every suite run — whether triggered via
 * the HTTP API, {@code RuntimeTestSuiteRunner}, or any other mechanism.
 *
 * <h2>Example — write a report after every run</h2>
 * <pre>{@code
 * testFrameworkService.addListener(result -> {
 *     reportWriter.write(result);
 * });
 * }</pre>
 *
 * @see no.testframework.javalibrary.spring.TestFrameworkService
 */
@FunctionalInterface
public interface SuiteRunListener {

    /**
     * Called once after a suite run completes (pass or fail).
     *
     * <p>Invoked on the same thread that ran the suite. Implementations should
     * be fast and non-blocking — offload heavy work to a background thread if needed.
     *
     * @param result the completed suite result
     */
    void onSuiteCompleted(TestSuiteExecutionResult result);
}
