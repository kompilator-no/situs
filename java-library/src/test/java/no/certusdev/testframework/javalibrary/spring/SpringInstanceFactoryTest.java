package no.certusdev.testframework.javalibrary.spring;

import no.certusdev.testframework.javalibrary.domain.TestCaseExecutionResult;
import no.certusdev.testframework.javalibrary.fixtures.TestSuiteFixtures;
import no.certusdev.testframework.javalibrary.runtime.InstanceFactory;
import no.certusdev.testframework.javalibrary.runtime.TestRunner;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SpringInstanceFactory}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Suite classes registered as Spring beans are resolved from the context
 *       and receive their dependencies injected.</li>
 *   <li>Suite classes that are NOT Spring beans fall back to reflective instantiation.</li>
 *   <li>Suite classes with no no-arg constructor and no bean registration fail clearly.</li>
 * </ul>
 */
class SpringInstanceFactoryTest {

    // -------------------------------------------------------------------------
    // Spring context config for DI tests
    // -------------------------------------------------------------------------

    @Configuration
    static class TestConfig {
        @Bean
        public TestSuiteFixtures.GreetingService greetingService() {
            return new TestSuiteFixtures.GreetingService("Hello");
        }

        @Bean
        public TestSuiteFixtures.DiSuite diSuite(TestSuiteFixtures.GreetingService svc) {
            return new TestSuiteFixtures.DiSuite(svc);
        }
    }

    private ApplicationContext buildContext() {
        return new AnnotationConfigApplicationContext(TestConfig.class);
    }

    // -------------------------------------------------------------------------
    // DI via Spring bean
    // -------------------------------------------------------------------------

    @Test
    void resolvesBeanFromContextAndInjectsDependencies() {
        ApplicationContext ctx = buildContext();
        SpringInstanceFactory factory = new SpringInstanceFactory(ctx);

        Object instance = factory.createInstance(TestSuiteFixtures.DiSuite.class);

        assertThat(instance).isInstanceOf(TestSuiteFixtures.DiSuite.class);
    }

    @Test
    void injectedSuiteTestPassesWithDependencyAvailable() {
        ApplicationContext ctx = buildContext();
        TestRunner runner = new TestRunner(new SpringInstanceFactory(ctx));

        List<TestCaseExecutionResult> results =
                runner.runTests(TestSuiteFixtures.DiSuite.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isPassed())
                .as("DiSuite.greetingReturnsExpectedValue should pass when GreetingService is injected")
                .isTrue();
        assertThat(results.get(0).getErrorMessage()).isNull();
    }

    @Test
    void injectedSuiteReturnsSameInstanceAsContext() {
        ApplicationContext ctx = buildContext();
        SpringInstanceFactory factory = new SpringInstanceFactory(ctx);

        Object fromFactory = factory.createInstance(TestSuiteFixtures.DiSuite.class);
        Object fromContext = ctx.getBean(TestSuiteFixtures.DiSuite.class);

        // SpringInstanceFactory returns the Spring-managed bean — same instance
        assertThat(fromFactory).isSameAs(fromContext);
    }

    // -------------------------------------------------------------------------
    // Reflective fallback for non-bean suites
    // -------------------------------------------------------------------------

    @Test
    void nonBeanSuiteFallsBackToReflection() {
        ApplicationContext ctx = buildContext(); // NonBeanSuite is NOT in this context
        SpringInstanceFactory factory = new SpringInstanceFactory(ctx);

        TestSuiteFixtures.NonBeanSuite.wasInstantiated = false;

        Object instance = factory.createInstance(TestSuiteFixtures.NonBeanSuite.class);

        assertThat(instance).isInstanceOf(TestSuiteFixtures.NonBeanSuite.class);
        assertThat(TestSuiteFixtures.NonBeanSuite.wasInstantiated)
                .as("Reflective fallback must call the no-arg constructor")
                .isTrue();
    }

    @Test
    void nonBeanSuiteRunsSuccessfullyWithReflectiveFallback() {
        ApplicationContext ctx = buildContext();
        TestRunner runner = new TestRunner(new SpringInstanceFactory(ctx));

        List<TestCaseExecutionResult> results =
                runner.runTests(TestSuiteFixtures.NonBeanSuite.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isPassed()).isTrue();
    }

    // -------------------------------------------------------------------------
    // No bean + no no-arg constructor → clear failure
    // -------------------------------------------------------------------------

    @Test
    void suiteWithNoNoArgConstructorAndNoBeanFailsClearly() {
        // Empty context — DiSuite has no no-arg constructor and is not a bean here
        AnnotationConfigApplicationContext emptyCtx = new AnnotationConfigApplicationContext();
        emptyCtx.refresh();
        SpringInstanceFactory factory = new SpringInstanceFactory(emptyCtx);

        assertThatThrownBy(() -> factory.createInstance(TestSuiteFixtures.DiSuite.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DiSuite");
    }

    // -------------------------------------------------------------------------
    // InstanceFactory.reflective() static factory
    // -------------------------------------------------------------------------

    @Test
    void reflectiveFactoryInstantiatesViaNoArgConstructor() {
        InstanceFactory factory = InstanceFactory.reflective();

        Object instance = factory.createInstance(TestSuiteFixtures.NonBeanSuite.class);

        assertThat(instance).isInstanceOf(TestSuiteFixtures.NonBeanSuite.class);
    }

    @Test
    void reflectiveFactoryFailsForClassWithNoNoArgConstructor() {
        InstanceFactory factory = InstanceFactory.reflective();

        assertThatThrownBy(() -> factory.createInstance(TestSuiteFixtures.DiSuite.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DiSuite");
    }
}
