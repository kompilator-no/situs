package no.testframework.javalibrary.runtime;

import no.testframework.javalibrary.annotations.RunTimeTest;
import no.testframework.javalibrary.annotations.RuntimeTestSuite;
import no.testframework.javalibrary.domain.TestSuiteDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestSuiteRegistryTest {

    private final TestSuiteRegistry registry = new TestSuiteRegistry();

    // -------------------------------------------------------------------------
    // Fixture suites
    // -------------------------------------------------------------------------

    @RuntimeTestSuite(name = "Suite A", description = "First suite")
    static class SuiteA {
        @RunTimeTest(name = "test1") public void test1() {}
        @RunTimeTest(name = "test2") public void test2() {}
    }

    @RuntimeTestSuite(name = "Suite B", description = "Second suite")
    static class SuiteB {
        @RunTimeTest(name = "onlyTest") public void onlyTest() {}
    }

    static class NotASuite {
        public void someMethod() {}
    }

    @RuntimeTestSuite   // no name — should fall back to simple class name
    static class UnnamedSuite {
        @RunTimeTest public void aTest() {}
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
}
