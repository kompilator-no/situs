package no.testframework.sampleapp;

import no.testframework.javalibrary.spring.EnableRuntimeTests;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sample application demonstrating the test framework.
 *
 * <p>{@code @EnableRuntimeTests} activates the framework explicitly.
 * In a Spring Boot app this is optional — the framework also activates
 * automatically via Spring Boot auto-configuration just by having the
 * library JAR on the classpath.  The annotation is useful for non-Boot
 * Spring applications or when you want the intent to be self-documenting.
 */
@SpringBootApplication
@EnableRuntimeTests
public class SampleAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleAppApplication.class, args);
    }
}
