package no.testframework.sampleapp.tests;

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

    /**
     * Returns the sum of {@code a} and {@code b}.
     *
     * @param a first operand
     * @param b second operand
     * @return {@code a + b}
     */
    public int add(int a, int b) {
        return a + b;
    }

    /**
     * Returns the difference of {@code a} minus {@code b}.
     *
     * @param a minuend
     * @param b subtrahend
     * @return {@code a - b}
     */
    public int subtract(int a, int b) {
        return a - b;
    }

    /**
     * Returns the product of {@code a} and {@code b}.
     *
     * @param a first factor
     * @param b second factor
     * @return {@code a * b}
     */
    public int multiply(int a, int b) {
        return a * b;
    }

    /**
     * Returns the integer quotient of {@code a} divided by {@code b}.
     *
     * @param a dividend
     * @param b divisor — must not be zero
     * @return {@code a / b}
     * @throws ArithmeticException if {@code b} is zero
     */
    public int divide(int a, int b) {
        if (b == 0) throw new ArithmeticException("Division by zero");
        return a / b;
    }
}
