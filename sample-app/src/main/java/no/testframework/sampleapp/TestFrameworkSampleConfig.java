package no.testframework.sampleapp;

import no.testframework.javalibrary.suite.SuiteApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestFrameworkSampleConfig {

    @Bean
    SuiteApi suiteApi() {
        return SuiteApi.create();
    }
}
