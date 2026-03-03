package no.testframework.sampleapp;

import no.testframework.javalibrary.api.TestFrameworkApi;
import no.testframework.javalibrary.runtime.TestSuiteResult;
import org.springframework.stereotype.Service;

@Service
public class SampleSuiteService {
    private final TestFrameworkApi testFrameworkApi;
    private final SampleSuiteFactory sampleSuiteFactory;

    public SampleSuiteService(TestFrameworkApi testFrameworkApi, SampleSuiteFactory sampleSuiteFactory) {
        this.testFrameworkApi = testFrameworkApi;
        this.sampleSuiteFactory = sampleSuiteFactory;
    }

    public TestSuiteResult runSampleSuite() {
        return testFrameworkApi.runSuite(sampleSuiteFactory.createLoginHappyPathSuite());
    }
}
