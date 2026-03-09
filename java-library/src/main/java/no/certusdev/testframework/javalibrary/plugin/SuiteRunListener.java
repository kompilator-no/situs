package no.certusdev.testframework.javalibrary.plugin;

/**
 * Callback interface notified during and after test suite execution.
 *
 * <p>Register implementations with
 * {@link no.certusdev.testframework.javalibrary.service.TestFrameworkService#addListener(SuiteRunListener)}
 * to receive results automatically after every suite run — whether triggered via
 * the HTTP API, {@code RuntimeTestSuiteRunner}, or any other mechanism.
 *
 * <h2>Example — write a report after every run</h2>
 * <pre>{@code
 * testFrameworkService.addListener(event -> {
 *     reportWriter.write(event.result());
 * });
 * }</pre>
 *
 * @see no.certusdev.testframework.javalibrary.service.TestFrameworkService
 */
@FunctionalInterface
public interface SuiteRunListener {

    /**
     * Called whenever a single test result becomes available while a suite is running.
     *
     * <p>Default implementation is a no-op so existing listeners only interested in
     * completed suites do not need to change.
     *
     * @param event the incremental test-completed event
     */
    default void onTestCompleted(TestCompletedEvent event) {
        // no-op by default
    }

    /**
     * Called once after a suite run completes (pass or fail).
     *
     * <p>Invoked on the same thread that ran the suite. Implementations should
     * be fast and non-blocking — offload heavy work to a background thread if needed.
     *
     * @param event the completed suite event
     */
    void onSuiteCompleted(SuiteCompletedEvent event);
}
