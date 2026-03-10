package no.kompilator.situs.springfixtures;

import no.kompilator.situs.annotations.Test;
import no.kompilator.situs.annotations.TestSuite;

@TestSuite(name = "AutoConfig Suite One")
public class AutoConfigSuiteOne {

    @Test(name = "first")
    public void first() {
    }
}
