package no.testframework.sampleapp.tests;

import no.testframework.javalibrary.annotations.RunTimeTest;
import no.testframework.javalibrary.annotations.RuntimeTestSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sample runtime test suite that exercises {@link Calculator} via Spring DI.
 *
 * <p>Annotated with {@code @Component} so the framework resolves it from the
 * {@code ApplicationContext} and injects {@link Calculator} via the constructor.
 */
@Component
@RuntimeTestSuite(name = "CalculatorTestSuite", description = "Tests for the Calculator class")
public class CalculatorTestSuite {

    private static final Logger log = LoggerFactory.getLogger(CalculatorTestSuite.class);

    private final Calculator calculator;

    public CalculatorTestSuite(Calculator calculator) {
        this.calculator = calculator;
        log.debug("CalculatorTestSuite created with injected Calculator");
    }

    @RunTimeTest(name = "addition", description = "2 + 3 should equal 5")
    public void testAddition() {
        assertThat(calculator.add(2, 3)).isEqualTo(5);
    }

    @RunTimeTest(name = "subtraction", description = "10 - 4 should equal 6")
    public void testSubtraction() {
        assertThat(calculator.subtract(10, 4)).isEqualTo(6);
    }

    @RunTimeTest(name = "multiplication", description = "3 * 7 should equal 21")
    public void testMultiplication() {
        assertThat(calculator.multiply(3, 7)).isEqualTo(21);
    }

    @RunTimeTest(name = "division", description = "20 / 4 should equal 5")
    public void testDivision() {
        assertThat(calculator.divide(20, 4)).isEqualTo(5);
    }

    @RunTimeTest(name = "divisionByZero", description = "Division by zero should throw ArithmeticException")
    public void testDivisionByZero() {
        assertThatThrownBy(() -> calculator.divide(10, 0))
                .isInstanceOf(ArithmeticException.class)
                .hasMessageContaining("zero");
    }
}
