package no.testframework.javalibrary.runtime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import no.testframework.javalibrary.annotations.RuntimeTestSuite;
import no.testframework.javalibrary.annotations.RunTimeTest;
import org.junit.jupiter.api.Assertions;


@RuntimeTestSuite(name = "Sample Suite", description = "A sample runtime test suite")
public class SampleTestSuite {
    @RunTimeTest(name = "testAddition")
    public void testAddition() {
        int result = 2 + 2;
        assertThat(result).isEqualTo(4);
    }

    @RunTimeTest(name = "testTrue")
    public void testTrue() {
        assertThat(1 < 2).isTrue();


    }

    @RunTimeTest(name = "testFail")
    public void testFail() {
        assertThat("fail").withThreadDumpOnError(); // This will fail
    }
}
