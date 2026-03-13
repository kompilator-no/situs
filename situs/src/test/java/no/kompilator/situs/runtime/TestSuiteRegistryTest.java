package no.kompilator.situs.runtime;

import no.kompilator.situs.domain.TestCaseDefinition;
import no.kompilator.situs.domain.TestSuiteDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSuiteRegistryTest {

    private final TestSuiteRegistry registry = new TestSuiteRegistry();

    // -------------------------------------------------------------------------
    // Fixture suites
    // -------------------------------------------------------------------------

    @no.kompilator.situs.annotations.TestSuite(name = "Suite A", description = "First suite")
    static class SuiteA {
        @no.kompilator.situs.annotations.Test(name = "test1") public void test1() {}
        @no.kompilator.situs.annotations.Test(name = "test2") public void test2() {}
    }

    @no.kompilator.situs.annotations.TestSuite(name = "Suite B", description = "Second suite")
    static class SuiteB {
        @no.kompilator.situs.annotations.Test(name = "onlyTest") public void onlyTest() {}
    }

    static class NotASuite {
        public void someMethod() {}
    }

    @no.kompilator.situs.annotations.TestSuite   // no name — should fall back to simple class name
    static class UnnamedSuite {
        @no.kompilator.situs.annotations.Test public void aTest() {}
    }

    @no.kompilator.situs.annotations.TestSuite(name = "Retry Suite", description = "Has retries configured")
    static class RetrySuite {
        @no.kompilator.situs.annotations.Test(name = "noRetry")           public void noRetry() {}
        @no.kompilator.situs.annotations.Test(name = "withRetry", retries = 3) public void withRetry() {}
    }

    @no.kompilator.situs.annotations.TestSuite(name = "Ordered Suite")
    static class OrderedSuite {
        @no.kompilator.situs.annotations.Test(name = "later", order = 2) public void bMethod() {}
        @no.kompilator.situs.annotations.Test(name = "earlier", order = 1) public void aMethod() {}
        @no.kompilator.situs.annotations.Test(name = "same-order-b", order = 3) public void zMethod() {}
        @no.kompilator.situs.annotations.Test(name = "same-order-a", order = 3) public void cMethod() {}
    }

    @no.kompilator.situs.annotations.TestSuite(name = "Duplicate Suite")
    static class DuplicateSuiteA {
        @no.kompilator.situs.annotations.Test(name = "first") public void first() {}
    }

    @no.kompilator.situs.annotations.TestSuite(name = "Duplicate Suite")
    static class DuplicateSuiteB {
        @no.kompilator.situs.annotations.Test(name = "second") public void second() {}
    }

    @no.kompilator.situs.annotations.TestSuite(name = "Duplicate Tests Suite")
    static class DuplicateTestsSuite {
        @no.kompilator.situs.annotations.Test(name = "same") public void first() {}
        @no.kompilator.situs.annotations.Test(name = "same") public void second() {}
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void discoversSuiteAnnotatedClasses() {
        List<TestSuiteDefinition> suites = registry.getAllSuites(Set.of(SuiteA.class, SuiteB.class));

        assertThat(suites).hasSize(2);
        assertThat(suites).extracting(TestSuiteDefinition::getName)
                .containsExactlyInAnyOrder("Suite A", "Suite B");
    }

    @Test
    void ignoresClassesWithoutAnnotation() {
        List<TestSuiteDefinition> suites = registry.getAllSuites(Set.of(SuiteA.class, NotASuite.class));

        assertThat(suites).hasSize(1);
        assertThat(suites.get(0).getName()).isEqualTo("Suite A");
    }

    @Test
    void discoversAllTestCasesInSuite() {
        List<TestSuiteDefinition> suites = registry.getAllSuites(Set.of(SuiteA.class));

        assertThat(suites.get(0).getTestCases()).hasSize(2);
        assertThat(suites.get(0).getTestCases()).extracting(tc -> tc.getName())
                .containsExactlyInAnyOrder("test1", "test2");
    }

    @Test
    void suiteDescriptionIsPreserved() {
        List<TestSuiteDefinition> suites = registry.getAllSuites(Set.of(SuiteA.class));

        assertThat(suites.get(0).getDescription()).isEqualTo("First suite");
    }

    @Test
    void suiteClassReferenceIsPreserved() {
        List<TestSuiteDefinition> suites = registry.getAllSuites(Set.of(SuiteA.class));

        assertThat(suites.get(0).getSuiteClass()).isEqualTo(SuiteA.class);
    }

    @Test
    void suiteNameFallsBackToSimpleClassNameWhenNotSet() {
        List<TestSuiteDefinition> suites = registry.getAllSuites(Set.of(UnnamedSuite.class));

        assertThat(suites.get(0).getName()).isEqualTo("UnnamedSuite");
    }

    @Test
    void emptySetReturnsNoSuites() {
        List<TestSuiteDefinition> suites = registry.getAllSuites(Set.of());

        assertThat(suites).isEmpty();
    }

    @Test
    void duplicateSuiteNamesFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(DuplicateSuiteA.class, DuplicateSuiteB.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate suite name(s)")
                .hasMessageContaining("Duplicate Suite");
    }

    @Test
    void duplicateTestNamesWithinSuiteFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(DuplicateTestsSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate test name(s)")
                .hasMessageContaining("Duplicate Tests Suite")
                .hasMessageContaining("same");
    }

    @Test
    void testsAreOrderedByOrderThenMethodName() {
        List<TestSuiteDefinition> suites = registry.getAllSuites(Set.of(OrderedSuite.class));

        assertThat(suites.getFirst().getTestCases())
                .extracting(TestCaseDefinition::getName)
                .containsExactly("earlier", "later", "same-order-a", "same-order-b");
    }

    // -------------------------------------------------------------------------
    // Retries
    // -------------------------------------------------------------------------

    @Test
    void retriesDefaultIsZeroWhenNotSet() {
        List<TestSuiteDefinition> suites = registry.getAllSuites(Set.of(RetrySuite.class));
        TestCaseDefinition noRetry = suites.get(0).getTestCases().stream()
                .filter(t -> t.getName().equals("noRetry")).findFirst().orElseThrow();

        assertThat(noRetry.getRetries()).isEqualTo(0);
    }

    @Test
    void retriesValueIsReadFromAnnotation() {
        List<TestSuiteDefinition> suites = registry.getAllSuites(Set.of(RetrySuite.class));
        TestCaseDefinition withRetry = suites.get(0).getTestCases().stream()
                .filter(t -> t.getName().equals("withRetry")).findFirst().orElseThrow();

        assertThat(withRetry.getRetries()).isEqualTo(3);
    }

    @Test
    void durationTimeoutIsConvertedToMilliseconds() {
        List<TestSuiteDefinition> suites = registry.getAllSuites(Set.of(
                no.kompilator.situs.fixtures.TestSuiteFixtures.DurationTimeoutSuite.class));

        assertThat(suites.getFirst().getTestCases().getFirst().getTimeoutMs()).isEqualTo(500);
    }

    @Test
    void parameterizedDurationTimeoutIsAppliedToEachInvocation() {
        List<TestSuiteDefinition> suites = registry.getAllSuites(Set.of(
                no.kompilator.situs.fixtures.TestSuiteFixtures.ParameterizedDurationTimeoutSuite.class));

        assertThat(suites.getFirst().getTestCases())
                .extracting(TestCaseDefinition::getTimeoutMs)
                .containsExactly(200L, 200L);
    }

    @Test
    void invalidTimeoutValuesFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.situs.fixtures.TestSuiteFixtures.InvalidTimeoutSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid timeoutMs")
                .hasMessageContaining("badTimeout");
    }

    @Test
    void invalidDurationTimeoutValuesFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.situs.fixtures.TestSuiteFixtures.InvalidDurationTimeoutSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid timeout")
                .hasMessageContaining("badDuration");
    }

    @Test
    void timeoutMsAndDurationCannotBeCombined() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.situs.fixtures.TestSuiteFixtures.ConflictingTimeoutSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot declare both timeoutMs and timeout");
    }

    @Test
    void invalidDelayValuesFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.situs.fixtures.TestSuiteFixtures.InvalidDelaySuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid delayMs")
                .hasMessageContaining("badDelay");
    }

    @Test
    void invalidRetryValuesFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.situs.fixtures.TestSuiteFixtures.InvalidRetrySuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid retries")
                .hasMessageContaining("badRetry");
    }

    @Test
    void testMethodsWithParametersFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.situs.fixtures.TestSuiteFixtures.ParameterizedTestSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Test method")
                .hasMessageContaining("must not declare parameters");
    }

    @Test
    void parameterizedTestsExpandIntoMultipleLogicalTestCases() {
        List<TestSuiteDefinition> suites = registry.getAllSuites(Set.of(
                no.kompilator.situs.fixtures.TestSuiteFixtures.ParameterizedValueSuite.class));

        assertThat(suites).hasSize(1);
        assertThat(suites.getFirst().getTestCases())
                .extracting(TestCaseDefinition::getName)
                .containsExactly("value[1]=alpha", "value[2]=beta", "value[3]=gamma");
    }

    @Test
    void parameterizedTestsPreserveInvocationArguments() {
        List<TestSuiteDefinition> suites = registry.getAllSuites(Set.of(
                no.kompilator.situs.fixtures.TestSuiteFixtures.ParameterizedCsvSuite.class));

        assertThat(suites.getFirst().getTestCases())
                .extracting(TestCaseDefinition::getArguments)
                .hasSize(2);
        assertThat(suites.getFirst().getTestCases().getFirst().getArguments())
                .containsExactly(1, 2, 3);
    }

    @Test
    void parameterizedTestsWithoutSourceFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.situs.fixtures.TestSuiteFixtures.ParameterizedMissingSourceSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must declare at least one argument source");
    }

    @Test
    void valueSourceRequiresExactlyOneConfiguredAttribute() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.situs.fixtures.TestSuiteFixtures.ParameterizedInvalidValueSourceSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@ValueSource")
                .hasMessageContaining("exactly one non-empty attribute");
    }

    @Test
    void nullSourceRejectsPrimitiveParameters() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.situs.fixtures.TestSuiteFixtures.ParameterizedPrimitiveNullSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Null arguments cannot be used with primitive parameter");
    }

    @Test
    void privateTestMethodsFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.situs.fixtures.TestSuiteFixtures.PrivateTestSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Test method")
                .hasMessageContaining("must be public");
    }

    @Test
    void staticTestMethodsFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.situs.fixtures.TestSuiteFixtures.StaticTestSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Test method")
                .hasMessageContaining("must not be static");
    }

    @Test
    void lifecycleMethodsWithParametersFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.situs.fixtures.TestSuiteFixtures.ParameterizedBeforeEachSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@BeforeEach method")
                .hasMessageContaining("must not declare parameters");
    }

    @Test
    void privateLifecycleMethodsFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.situs.fixtures.TestSuiteFixtures.PrivateBeforeAllSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@BeforeAll method")
                .hasMessageContaining("must be public");
    }

    @Test
    void staticLifecycleMethodsFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.situs.fixtures.TestSuiteFixtures.StaticAfterAllSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@AfterAll method")
                .hasMessageContaining("must not be static");
    }
}
