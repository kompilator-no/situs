package no.testframework.sampleapp;

import no.testframework.javalibrary.suite.SuiteApi;
import no.testframework.javalibrary.suite.SuiteResult;
import org.springframework.stereotype.Service;

@Service
public class SampleSuiteService {
    private final SuiteApi suiteApi;

    public SampleSuiteService(SuiteApi suiteApi) {
        this.suiteApi = suiteApi;
    }

    public SuiteResult runSampleSuite() {
        return suiteApi.runSuite(new SampleSuiteDefinition());
    }
}
