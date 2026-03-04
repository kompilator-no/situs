package no.testframework.javalibrary.suite;

import java.util.Objects;

public final class SuiteApi {
    private final SuiteRunner runner;

    public SuiteApi(SuiteRunner runner) {
        this.runner = Objects.requireNonNull(runner, "runner cannot be null");
    }

    public static SuiteApi create() {
        return new SuiteApi(new SuiteRunner());
    }

    public SuiteResult runSuite(TestSuite suite) {
        return runner.run(suite);
    }
}
