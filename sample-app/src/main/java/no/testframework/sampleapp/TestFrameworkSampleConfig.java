package no.testframework.sampleapp;

import no.testframework.javalibrary.api.TestFrameworkApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestFrameworkSampleConfig {

    @Bean
    TestFrameworkApi testFrameworkApi() {
        return TestFrameworkApi.withDefaults();
    }
}
