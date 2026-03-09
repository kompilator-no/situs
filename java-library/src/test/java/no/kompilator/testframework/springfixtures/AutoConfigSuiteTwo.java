package no.kompilator.testframework.springfixtures;

import no.kompilator.testframework.annotations.Test;
import no.kompilator.testframework.annotations.TestSuite;

@TestSuite(name = "AutoConfig Suite Two")
public class AutoConfigSuiteTwo {

    @Test(name = "second")
    public void second() {
    }
}
