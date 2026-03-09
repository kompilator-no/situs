package no.certusdev.testframework.sampleapp.tests;

import org.springframework.stereotype.Service;

/**
 * A simple arithmetic calculator used as the subject under test in
 * {@link CalculatorTestSuite}.
 *
 * <p>Registered as a Spring {@code @Service} so it can be injected into
 * {@link CalculatorTestSuite} via constructor injection, demonstrating the
 * framework's Spring DI support for test suite classes.
 */
@Service
public class Calculator {

    /** @return {@code a + b} */
    public int add(int a, int b) { return a + b; }

    /** @return {@code a - b} */
    public int subtract(int a, int b) { return a - b; }

    /** @return {@code a * b} */
    public int multiply(int a, int b) { return a * b; }

    /**
     * @return {@code a / b}
     * @throws ArithmeticException if {@code b} is zero
     */
    public int divide(int a, int b) {
        if (b == 0) throw new ArithmeticException("Division by zero");
        return a / b;
    }
}
