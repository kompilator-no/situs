package no.kompilator.situs.springfixtures;

import no.kompilator.situs.annotations.Test;
import no.kompilator.situs.annotations.TestSuite;

@TestSuite(name = "AutoConfig Suite Two")
public class AutoConfigSuiteTwo {

    @Test(name = "second")
    public void second() {
    }
}
