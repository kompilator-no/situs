package no.testframework.sampleapp.tests;

import no.testframework.javalibrary.annotations.BeforeEach;
import no.testframework.javalibrary.annotations.RunTimeTest;
import no.testframework.javalibrary.annotations.RuntimeTestSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sample runtime test suite that exercises {@link Calculator}.
 * Annotated with {@code @RuntimeTestSuite} so the test framework discovers it
 * automatically via the registered {@code Set<Class<?>>} bean in
 * {@link no.testframework.sampleapp.config.TestFrameworkConfig}.
 */
@RuntimeTestSuite(name = "CalculatorTestSuite", description = "Tests for the Calculator class")
public class CalculatorTestSuite {

    private static final Logger log = LoggerFactory.getLogger(CalculatorTestSuite.class);

    private Calculator calculator;

    @BeforeEach
    public void setUp() {
        calculator = new Calculator();
        log.debug("Calculator instance created");
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
