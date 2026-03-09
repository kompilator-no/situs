package no.certusdev.testframework.kotlinapp.tests

import no.certusdev.testframework.javalibrary.annotations.Test
import no.certusdev.testframework.javalibrary.annotations.TestSuite
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Kotlin runtime test suite that exercises [Calculator] via Spring DI.
 *
 * ## Suite discovery
 * Discovered automatically via the `@TestSuite` annotation — no manual
 * registration needed.
 *
 * ## Dependency injection
 * Annotated with `@Component` so the framework resolves it from the `ApplicationContext`
 * via `SpringInstanceFactory`. The `kotlin.plugin.spring` plugin keeps the class `open`
 * so Spring can proxy it.
 *
 * ## Running via HTTP
 * ```
 * POST /api/test-framework/suites/CalculatorTestSuite/run
 * GET  /api/test-framework/runs/{runId}/status
 * ```
 */
@Component
@TestSuite(name = "CalculatorTestSuite", description = "Tests for the Calculator class")
class CalculatorTestSuite(private val calculator: Calculator) {

    private val log = LoggerFactory.getLogger(CalculatorTestSuite::class.java)

    init {
        log.debug("CalculatorTestSuite created with injected Calculator")
    }

    @Test(name = "addition", description = "2 + 3 should equal 5")
    fun testAddition() {
        assertThat(calculator.add(2, 3)).isEqualTo(5)
    }

    @Test(name = "subtraction", description = "10 - 4 should equal 6")
    fun testSubtraction() {
        assertThat(calculator.subtract(10, 4)).isEqualTo(6)
    }

    @Test(name = "multiplication", description = "3 * 7 should equal 21")
    fun testMultiplication() {
        assertThat(calculator.multiply(3, 7)).isEqualTo(21)
    }

    @Test(name = "division", description = "20 / 4 should equal 5")
    fun testDivision() {
        assertThat(calculator.divide(20, 4)).isEqualTo(5)
    }

    @Test(name = "divisionByZero", description = "Division by zero should throw ArithmeticException")
    fun testDivisionByZero() {
        assertThatThrownBy { calculator.divide(10, 0) }
            .isInstanceOf(ArithmeticException::class.java)
            .hasMessageContaining("zero")
    }
}
