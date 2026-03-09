package no.kompilator.testframework.plugin;

import no.kompilator.testframework.model.TestSuite;
import no.kompilator.testframework.model.TestSuiteResult;

/**
 * Public event emitted when a suite run completes.
 *
 * <p>Contains both the discovered suite metadata and the final execution result so
 * plugins can react without depending on the runtime engine's internal domain types.
 *
 * @param suite  the discovered suite metadata
 * @param result the final public execution result
 */
public record SuiteCompletedEvent(TestSuite suite, TestSuiteResult result) {
}
