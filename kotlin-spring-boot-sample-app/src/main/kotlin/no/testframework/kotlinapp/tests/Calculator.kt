package no.testframework.kotlinapp.tests

import org.springframework.stereotype.Service

/**
 * A simple arithmetic calculator used as the subject under test in [CalculatorTestSuite].
 *
 * Registered as a Spring `@Service` so it can be injected into [CalculatorTestSuite]
 * via constructor injection, demonstrating the framework's Spring DI support.
 */
@Service
class Calculator {

    /** Returns `a + b`. */
    fun add(a: Int, b: Int): Int = a + b

    /** Returns `a - b`. */
    fun subtract(a: Int, b: Int): Int = a - b

    /** Returns `a * b`. */
    fun multiply(a: Int, b: Int): Int = a * b

    /**
     * Returns `a / b`.
     * @throws ArithmeticException if [b] is zero
     */
    fun divide(a: Int, b: Int): Int {
        if (b == 0) throw ArithmeticException("Division by zero")
        return a / b
    }
}
