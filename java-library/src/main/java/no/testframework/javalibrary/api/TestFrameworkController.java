package no.testframework.javalibrary.api;

import no.testframework.javalibrary.domain.TestSuite;
import no.testframework.javalibrary.runtime.TestSuiteResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Predefined Spring controller that can be reused directly in consuming apps.
 */
@RestController
@RequestMapping("/api/test-framework")
public final class TestFrameworkController {
    private final TestFrameworkApi api;

    public TestFrameworkController(TestFrameworkApi api) {
        this.api = Objects.requireNonNull(api, "api cannot be null");
    }

    @PostMapping("/suites/run")
    public TestSuiteResult runSuite(@RequestBody TestSuite suite) {
        return api.runSuite(suite);
    }
}
