package no.kompilator.testframework.springfixtures;

import no.kompilator.testframework.annotations.Test;
import no.kompilator.testframework.annotations.TestSuite;

@TestSuite(name = "AutoConfig Suite One")
public class AutoConfigSuiteOne {

    @Test(name = "first")
    public void first() {
    }
}
