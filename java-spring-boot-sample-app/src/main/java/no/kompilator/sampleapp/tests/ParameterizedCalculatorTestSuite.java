package no.kompilator.sampleapp.tests;

import no.kompilator.situs.annotations.CsvSource;
import no.kompilator.situs.annotations.MethodSource;
import no.kompilator.situs.annotations.ParameterizedTest;
import no.kompilator.situs.annotations.TestSuite;
import no.kompilator.situs.params.Arguments;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Component
@TestSuite(name = "ParameterizedCalculatorTestSuite", description = "Parameterized tests for the Calculator class")
public class ParameterizedCalculatorTestSuite {

    private final Calculator calculator;

    public ParameterizedCalculatorTestSuite(Calculator calculator) {
        this.calculator = calculator;
    }

    @ParameterizedTest(name = "add[{index}] {0}+{1}={2}")
    @CsvSource({"1,2,3", "20,22,42", "-5,8,3"})
    public void additionCases(int left, int right, int expected) {
        assertThat(calculator.add(left, right)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "multiply[{index}] {0}*{1}={2}")
    @MethodSource("multiplicationCases")
    public void multiplicationCases(int left, int right, int expected) {
        assertThat(calculator.multiply(left, right)).isEqualTo(expected);
    }

    public static Stream<Arguments> multiplicationCases() {
        return Stream.of(
                Arguments.of(2, 3, 6),
                Arguments.of(7, 6, 42));
    }
}
