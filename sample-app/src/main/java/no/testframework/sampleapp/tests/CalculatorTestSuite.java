package no.testframework.sampleapp.tests;

import no.testframework.javalibrary.annotations.RunTimeTest;
import no.testframework.javalibrary.annotations.RuntimeTestSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sample runtime test suite that exercises {@link Calculator}.
 *
 * <h2>Suite discovery</h2>
 * <p>Discovered automatically at startup — the framework's {@code ClasspathScanner}
 * finds this class via the {@code @RuntimeTestSuite} annotation. No manual registration
 * is needed.
 *
 * <h2>Dependency injection</h2>
 * <p>This class is also a Spring {@code @Component}, so the framework resolves it
 * from the {@code ApplicationContext} via {@code SpringInstanceFactory} rather than
 * calling {@code new}. The {@link Calculator} service is injected via the constructor,
 * demonstrating full Spring DI support in runtime test suites.
 *
 * <h2>Running via HTTP</h2>
 * <pre>
 * POST /api/test-framework/suites/CalculatorTestSuite/run
 * GET  /api/test-framework/runs/{runId}/status
 * </pre>
 */
@Component
@RuntimeTestSuite(name = "CalculatorTestSuite", description = "Tests for the Calculator class")
public class CalculatorTestSuite {

    private static final Logger log = LoggerFactory.getLogger(CalculatorTestSuite.class);

    private final Calculator calculator;

    /**
     * Constructor injection — {@link Calculator} is provided by the Spring context.
     *
     * @param calculator the calculator service to test
     */
    public CalculatorTestSuite(Calculator calculator) {
        this.calculator = calculator;
        log.debug("CalculatorTestSuite created with injected Calculator");
    }

    /**
     * Verifies that {@link Calculator#add(int, int)} returns the correct sum.
     */
    @RunTimeTest(name = "addition", description = "2 + 3 should equal 5")
    public void testAddition() {
        assertThat(calculator.add(2, 3)).isEqualTo(5);
    }

    /**
     * Verifies that {@link Calculator#subtract(int, int)} returns the correct difference.
     */
    @RunTimeTest(name = "subtraction", description = "10 - 4 should equal 6")
    public void testSubtraction() {
        assertThat(calculator.subtract(10, 4)).isEqualTo(6);
    }

    /**
     * Verifies that {@link Calculator#multiply(int, int)} returns the correct product.
     */
    @RunTimeTest(name = "multiplication", description = "3 * 7 should equal 21")
    public void testMultiplication() {
        assertThat(calculator.multiply(3, 7)).isEqualTo(21);
    }

    /**
     * Verifies that {@link Calculator#divide(int, int)} returns the correct quotient.
     */
    @RunTimeTest(name = "division", description = "20 / 4 should equal 5")
    public void testDivision() {
        assertThat(calculator.divide(20, 4)).isEqualTo(5);
    }

    /**
     * Verifies that {@link Calculator#divide(int, int)} throws {@link ArithmeticException}
     * when the divisor is zero.
     */
    @RunTimeTest(name = "divisionByZero", description = "Division by zero should throw ArithmeticException")
    public void testDivisionByZero() {
        assertThatThrownBy(() -> calculator.divide(10, 0))
                .isInstanceOf(ArithmeticException.class)
                .hasMessageContaining("zero");
    }
}
