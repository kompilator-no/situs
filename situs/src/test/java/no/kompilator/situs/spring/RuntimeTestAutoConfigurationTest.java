package no.kompilator.situs.spring;

import no.kompilator.situs.model.TestSuite;
import no.kompilator.situs.model.SuiteRunStatus;
import no.kompilator.situs.service.TestFrameworkService;
import no.kompilator.situs.fixtures.AsyncTestHelper;
import no.kompilator.situs.springfixtures.AutoConfigSuiteOne;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeTestAutoConfigurationTest {

    private final RuntimeTestAutoConfiguration autoConfiguration = new RuntimeTestAutoConfiguration();

    @Test
    void bootBasePackagesAreUsedWhenScanPackagesAreNotConfigured() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        AutoConfigurationPackages.register(context, AutoConfigSuiteOne.class.getPackageName());
        context.refresh();

        TestFrameworkProperties properties = new TestFrameworkProperties();
        TestFrameworkService service =
                autoConfiguration.testFrameworkService(context, context.getBeanFactory(), properties);

        List<TestSuite> suites = service.getAllSuites();

        assertThat(suites).extracting(TestSuite::getName)
                .contains("AutoConfig Suite One", "AutoConfig Suite Two");
    }

    @Test
    void configuredScanPackagesOverrideBootBasePackages() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        AutoConfigurationPackages.register(context, "no.kompilator.sampleapp");
        context.refresh();

        TestFrameworkProperties properties = new TestFrameworkProperties();
        properties.setScanPackages(List.of(AutoConfigSuiteOne.class.getPackageName()));

        TestFrameworkService service =
                autoConfiguration.testFrameworkService(context, context.getBeanFactory(), properties);

        assertThat(service.getAllSuites()).extracting(TestSuite::getName)
                .contains("AutoConfig Suite One", "AutoConfig Suite Two");
    }

    @Test
    void maxStoredRunsPropertyIsAppliedToCreatedService() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        AutoConfigurationPackages.register(context, AutoConfigSuiteOne.class.getPackageName());
        context.refresh();

        TestFrameworkProperties properties = new TestFrameworkProperties();
        properties.setMaxStoredRuns(1);

        TestFrameworkService service =
                autoConfiguration.testFrameworkService(context, context.getBeanFactory(), properties);

        String firstRunId = service.startSuiteAsync("AutoConfig Suite One");
        SuiteRunStatus first = AsyncTestHelper.awaitCompleted(service, firstRunId);
        assertThat(first.getStatus()).isEqualTo(SuiteRunStatus.Status.COMPLETED);

        String secondRunId = service.startSuiteAsync("AutoConfig Suite One");
        SuiteRunStatus second = AsyncTestHelper.awaitCompleted(service, secondRunId);
        assertThat(second.getStatus()).isEqualTo(SuiteRunStatus.Status.COMPLETED);

        assertThatThrownBy(() -> service.getRunStatus(firstRunId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(firstRunId);
        assertThat(service.getRunStatus(secondRunId).getStatus()).isEqualTo(SuiteRunStatus.Status.COMPLETED);
    }
}
