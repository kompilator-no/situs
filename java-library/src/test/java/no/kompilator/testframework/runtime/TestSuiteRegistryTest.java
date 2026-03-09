package no.kompilator.testframework.runtime;

import no.kompilator.testframework.domain.TestCaseDefinition;
import no.kompilator.testframework.domain.TestSuiteDefinition;
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

    @no.kompilator.testframework.annotations.TestSuite(name = "Suite A", description = "First suite")
    static class SuiteA {
        @no.kompilator.testframework.annotations.Test(name = "test1") public void test1() {}
        @no.kompilator.testframework.annotations.Test(name = "test2") public void test2() {}
    }

    @no.kompilator.testframework.annotations.TestSuite(name = "Suite B", description = "Second suite")
    static class SuiteB {
        @no.kompilator.testframework.annotations.Test(name = "onlyTest") public void onlyTest() {}
    }

    static class NotASuite {
        public void someMethod() {}
    }

    @no.kompilator.testframework.annotations.TestSuite   // no name — should fall back to simple class name
    static class UnnamedSuite {
        @no.kompilator.testframework.annotations.Test public void aTest() {}
    }

    @no.kompilator.testframework.annotations.TestSuite(name = "Retry Suite", description = "Has retries configured")
    static class RetrySuite {
        @no.kompilator.testframework.annotations.Test(name = "noRetry")           public void noRetry() {}
        @no.kompilator.testframework.annotations.Test(name = "withRetry", retries = 3) public void withRetry() {}
    }

    @no.kompilator.testframework.annotations.TestSuite(name = "Ordered Suite")
    static class OrderedSuite {
        @no.kompilator.testframework.annotations.Test(name = "later", order = 2) public void bMethod() {}
        @no.kompilator.testframework.annotations.Test(name = "earlier", order = 1) public void aMethod() {}
        @no.kompilator.testframework.annotations.Test(name = "same-order-b", order = 3) public void zMethod() {}
        @no.kompilator.testframework.annotations.Test(name = "same-order-a", order = 3) public void cMethod() {}
    }

    @no.kompilator.testframework.annotations.TestSuite(name = "Duplicate Suite")
    static class DuplicateSuiteA {
        @no.kompilator.testframework.annotations.Test(name = "first") public void first() {}
    }

    @no.kompilator.testframework.annotations.TestSuite(name = "Duplicate Suite")
    static class DuplicateSuiteB {
        @no.kompilator.testframework.annotations.Test(name = "second") public void second() {}
    }

    @no.kompilator.testframework.annotations.TestSuite(name = "Duplicate Tests Suite")
    static class DuplicateTestsSuite {
        @no.kompilator.testframework.annotations.Test(name = "same") public void first() {}
        @no.kompilator.testframework.annotations.Test(name = "same") public void second() {}
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
    void invalidTimeoutValuesFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.testframework.fixtures.TestSuiteFixtures.InvalidTimeoutSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid timeoutMs")
                .hasMessageContaining("badTimeout");
    }

    @Test
    void invalidDelayValuesFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.testframework.fixtures.TestSuiteFixtures.InvalidDelaySuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid delayMs")
                .hasMessageContaining("badDelay");
    }

    @Test
    void invalidRetryValuesFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.testframework.fixtures.TestSuiteFixtures.InvalidRetrySuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid retries")
                .hasMessageContaining("badRetry");
    }

    @Test
    void testMethodsWithParametersFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.testframework.fixtures.TestSuiteFixtures.ParameterizedTestSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Test method")
                .hasMessageContaining("must not declare parameters");
    }

    @Test
    void privateTestMethodsFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.testframework.fixtures.TestSuiteFixtures.PrivateTestSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Test method")
                .hasMessageContaining("must be public");
    }

    @Test
    void staticTestMethodsFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.testframework.fixtures.TestSuiteFixtures.StaticTestSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Test method")
                .hasMessageContaining("must not be static");
    }

    @Test
    void lifecycleMethodsWithParametersFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.testframework.fixtures.TestSuiteFixtures.ParameterizedBeforeEachSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@BeforeEach method")
                .hasMessageContaining("must not declare parameters");
    }

    @Test
    void privateLifecycleMethodsFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.testframework.fixtures.TestSuiteFixtures.PrivateBeforeAllSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@BeforeAll method")
                .hasMessageContaining("must be public");
    }

    @Test
    void staticLifecycleMethodsFailFast() {
        assertThatThrownBy(() -> registry.getAllSuites(Set.of(
                no.kompilator.testframework.fixtures.TestSuiteFixtures.StaticAfterAllSuite.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@AfterAll method")
                .hasMessageContaining("must not be static");
    }
}
