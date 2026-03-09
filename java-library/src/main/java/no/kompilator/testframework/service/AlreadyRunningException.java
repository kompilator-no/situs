package no.kompilator.testframework.service;

/**
 * Thrown when a suite or test run is requested but the same suite or test
 * is already {@code PENDING} or {@code RUNNING}.
 *
 * <p>The Spring controller maps this to an HTTP {@code 409 Conflict} response.
 * The response body contains a JSON object with an {@code error} field describing
 * which suite or test is already running.
 *
 * @see TestFrameworkService#startSuiteAsync(String)
 * @see TestFrameworkService#startSingleTestAsync(String, String)
 */
public class AlreadyRunningException extends RuntimeException {

    /**
     * @param message a descriptive message identifying the suite or test that is already running
     */
    public AlreadyRunningException(String message) {
        super(message);
    }
}
