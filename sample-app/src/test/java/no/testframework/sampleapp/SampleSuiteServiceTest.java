package no.testframework.sampleapp;

import no.testframework.javalibrary.suite.SuiteResult;
import no.testframework.javalibrary.runtime.TestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class SampleSuiteServiceTest {

    @Autowired
    private SampleSuiteService sampleSuiteService;

    @Test
    void sampleSuiteRunsSuccessfullyWithSuiteApi() {
        SuiteResult result = sampleSuiteService.runSampleSuite();

        assertEquals(TestStatus.PASSED, result.status());
        assertEquals(3, result.testCaseResults().size());
        assertEquals("endpoint 1 response", result.contextSnapshot().get("sampleHttpBody"));
        assertEquals("processed", result.contextSnapshot().get("sampleKafkaState"));
    }
}
