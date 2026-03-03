package no.testframework.sampleapp;

import no.testframework.javalibrary.runtime.TestStatus;
import no.testframework.javalibrary.runtime.TestSuiteResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class SampleSuiteServiceTest {

    @Autowired
    private SampleSuiteService sampleSuiteService;

    @Test
    void sampleSuiteRunsAllStepsSuccessfully() {
        TestSuiteResult result = sampleSuiteService.runSampleSuite();

        assertEquals(TestStatus.PASSED, result.status());
        assertEquals(2, result.stepResults().size());
        assertEquals(TestStatus.PASSED, result.stepResults().get(0).status());
        assertEquals(TestStatus.PASSED, result.stepResults().get(1).status());
        assertEquals("alice", result.contextSnapshot().get("username"));
        assertEquals("admin", result.contextSnapshot().get("role"));
    }
}
