package no.testframework.sampleapp;

import no.testframework.runnerlib.config.ExternalEnvironmentProperties;
import no.testframework.runnerlib.config.RunnerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({RunnerProperties.class, ExternalEnvironmentProperties.class})
public class SampleRunnerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleRunnerApplication.class, args);
    }
}
