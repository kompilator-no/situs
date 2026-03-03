package no.testframework.javalibrary.api;

import no.testframework.javalibrary.domain.TestSuite;
import no.testframework.javalibrary.runtime.TestRuntimeConfiguration;
import no.testframework.javalibrary.runtime.TestSuiteResult;
import no.testframework.javalibrary.runtime.TestSuiteRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * App-facing helper facade for executing suites.
 */
public final class TestFrameworkApi {
    private final TestSuiteRunner runner;
    private final List<TestSuite> registeredSuites = new ArrayList<>();

    public TestFrameworkApi(TestSuiteRunner runner) {
        this.runner = Objects.requireNonNull(runner, "runner cannot be null");
    }

    public static TestFrameworkApi withDefaults() {
        TestRuntimeConfiguration configuration = new TestRuntimeConfiguration();
        TestFrameworkApiHandlers.registerDefaultHandlers(configuration);
        return new TestFrameworkApi(configuration.buildRunner());
    }

    public static TestFrameworkApi create(Consumer<TestRuntimeConfiguration> configurationCustomizer) {
        Objects.requireNonNull(configurationCustomizer, "configurationCustomizer cannot be null");

        TestRuntimeConfiguration configuration = new TestRuntimeConfiguration();
        TestFrameworkApiHandlers.registerDefaultHandlers(configuration);
        configurationCustomizer.accept(configuration);
        return new TestFrameworkApi(configuration.buildRunner());
    }

    public TestSuiteResult runSuite(TestSuite suite) {
        TestSuite suiteToRun = Objects.requireNonNull(suite, "suite cannot be null");
        registerSuite(suiteToRun);
        return runner.run(suiteToRun);
    }

    public synchronized String getStatus() {
        return "UP";
    }

    public synchronized List<TestSuite> getTests() {
        return List.copyOf(registeredSuites);
    }

    public synchronized void registerSuite(TestSuite suite) {
        registeredSuites.add(Objects.requireNonNull(suite, "suite cannot be null"));
    }
}
