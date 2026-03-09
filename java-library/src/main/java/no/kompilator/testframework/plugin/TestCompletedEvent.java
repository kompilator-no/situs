package no.kompilator.testframework.plugin;

import no.kompilator.testframework.model.TestCaseResult;
import no.kompilator.testframework.model.TestSuite;

/**
 * Public event emitted whenever a single test result becomes available during a suite run.
 *
 * <p>This allows plugins to observe incremental progress without depending on
 * internal runtime classes.
 *
 * @param runId      the asynchronous run identifier
 * @param suite      the discovered suite metadata
 * @param testResult the completed public test result
 */
public record TestCompletedEvent(String runId, TestSuite suite, TestCaseResult testResult) {
}
