package no.testframework.javalibrary.runtime;

import no.testframework.javalibrary.annotations.RunTimeTest;
import no.testframework.javalibrary.annotations.RuntimeTestSuite;
import no.testframework.javalibrary.domain.TestSuiteExecutionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeTestSuiteRunnerTest {

    private final RuntimeTestSuiteRunner runner = new RuntimeTestSuiteRunner();

    // -------------------------------------------------------------------------
    // Fixture suites
    // -------------------------------------------------------------------------

    @RuntimeTestSuite(name = "All Pass", description = "All tests pass")
    static class AllPassSuite {
        @RunTimeTest(name = "a") public void a() {}
        @RunTimeTest(name = "b") public void b() {}
    }

    @RuntimeTestSuite(name = "One Fail", description = "One test fails")
    static class OneFailSuite {
        @RunTimeTest(name = "passes") public void passes() {}
        @RunTimeTest(name = "fails")  public void fails() { throw new AssertionError("oops"); }
    }

    static class NotAnnotated {}

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void suiteNameAndDescriptionArePreserved() {
        TestSuiteExecutionResult result = runner.runSuite(AllPassSuite.class);

        assertThat(result.getSuiteName()).isEqualTo("All Pass");
        assertThat(result.getDescription()).isEqualTo("All tests pass");
    }

    @Test
    void allPassingTestsResultInAllPassed() {
        TestSuiteExecutionResult result = runner.runSuite(AllPassSuite.class);

        assertThat(result.isAllPassed()).isTrue();
        assertThat(result.getPassedCount()).isEqualTo(2);
        assertThat(result.getFailedCount()).isEqualTo(0);
    }

    @Test
    void failingTestIsReflectedInCounts() {
        TestSuiteExecutionResult result = runner.runSuite(OneFailSuite.class);

        assertThat(result.isAllPassed()).isFalse();
        assertThat(result.getPassedCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
    }

    @Test
    void testCaseResultsAreIncluded() {
        TestSuiteExecutionResult result = runner.runSuite(OneFailSuite.class);

        assertThat(result.getTestCaseResults()).hasSize(2);
    }

    @Test
    void nonAnnotatedClassThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> runner.runSuite(NotAnnotated.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@RuntimeTestSuite");
    }
}
