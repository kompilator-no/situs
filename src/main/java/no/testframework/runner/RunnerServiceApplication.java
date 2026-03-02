package no.testframework.runner;

import no.testframework.runner.config.ExternalEnvironmentProperties;
import no.testframework.runner.config.RunnerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({RunnerProperties.class, ExternalEnvironmentProperties.class})
public class RunnerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RunnerServiceApplication.class, args);
    }
}
