package no.kompilator.situs.spring;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables Situs system integration testing in any Spring application.
 *
 * <p><b>Spring Boot apps</b> do not need this annotation — the framework activates
 * automatically via Spring Boot auto-configuration as soon as the JAR is on the
 * classpath. Use {@code @EnableRuntimeTests} only for explicit opt-in or for plain
 * (non-Boot) Spring applications.
 *
 * <h2>Usage — Spring Boot (explicit opt-in)</h2>
 * <pre>{@code
 * @SpringBootApplication
 * @EnableRuntimeTests
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 * }</pre>
 *
 * <h2>Usage — plain Spring (non-Boot)</h2>
 * <pre>{@code
 * @Configuration
 * @EnableRuntimeTests
 * public class AppConfig { }
 * }</pre>
 *
 * @see no.kompilator.situs.spring.RuntimeTestAutoConfiguration
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(RuntimeTestAutoConfiguration.class)
public @interface EnableRuntimeTests {
}
