package no.testframework.javalibrary.api.service;

/**
 * Thrown when a suite or test run is requested but the same suite/test
 * is already PENDING or RUNNING.
 */
public class AlreadyRunningException extends RuntimeException {

    public AlreadyRunningException(String message) {
        super(message);
    }
}
