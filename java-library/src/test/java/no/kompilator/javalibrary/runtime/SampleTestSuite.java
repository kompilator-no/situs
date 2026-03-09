package no.kompilator.javalibrary.runtime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import no.kompilator.javalibrary.annotations.AfterAll;
import no.kompilator.javalibrary.annotations.AfterEach;
import no.kompilator.javalibrary.annotations.BeforeAll;
import no.kompilator.javalibrary.annotations.BeforeEach;
import no.kompilator.javalibrary.annotations.TestSuite;
import no.kompilator.javalibrary.annotations.Test;


@TestSuite(name = "Sample Suite", description = "A sample runtime test suite")
public class SampleTestSuite {

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @BeforeAll
    public void suiteSetup() {
        System.out.println("[BeforeAll] Setting up suite resources");
    }

    @BeforeEach
    public void testSetup() {
        System.out.println("[BeforeEach] Preparing test state");
    }

    @AfterEach
    public void testTeardown() {
        System.out.println("[AfterEach] Cleaning up test state");
    }

    @AfterAll
    public void suiteTeardown() {
        System.out.println("[AfterAll] Releasing suite resources");
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test(name = "testAddition", description = "Verifies that 2 + 2 equals 4")
    public void testAddition() {
        int result = 2 + 2;
        assertThat(result).isEqualTo(4);
    }

    @Test(name = "testSubtraction", description = "Verifies that 10 - 3 equals 7")
    public void testSubtraction() {
        int result = 10 - 3;
        assertThat(result).isEqualTo(7);
    }

    @Test(name = "testMultiplication", description = "Verifies that 3 * 4 equals 12")
    public void testMultiplication() {
        int result = 3 * 4;
        assertThat(result).isEqualTo(12);
    }

    @Test(name = "testTrue", description = "Verifies that 1 < 2 is true")
    public void testTrue() {
        assertThat(1 < 2).isTrue();
    }

    @Test(name = "testStringNotEmpty", description = "Verifies that a non-blank string is not empty")
    public void testStringNotEmpty() {
        String value = "hello";
        assertThat(value.isEmpty()).isFalse();
    }

    @Test(name = "testNullCheck", description = "Verifies that a non-null value is not null")
    public void testNullCheck() {
        Object value = new Object();
        assertThat(value).isNotNull();
    }

    @Test(name = "testFail", description = "Intentional failure to demonstrate error reporting")
    public void testFail() {
        assertThat("actual").isEqualTo("expected");
    }

    @Test(name = "testTimeout", description = "Intentional timeout to demonstrate timeoutMs", timeoutMs = 200)
    public void testTimeout() throws InterruptedException {
        Thread.sleep(10_000); // will be cancelled after 200 ms
    }
}
