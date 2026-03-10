package no.kompilator.situs.spring.model;

/**
 * Minimal request body for the
 * {@code POST /api/test-framework/suites/run/by-name} endpoint.
 *
 * <p>Using a dedicated DTO rather than the full {@code TestSuite} model
 * avoids Jackson failing to deserialise partial bodies that omit the primitive
 * {@code parallel} field.
 *
 * @param name the suite name as registered in {@code @TestSuite#name()}
 */
public record RunSuiteRequest(String name) {}
