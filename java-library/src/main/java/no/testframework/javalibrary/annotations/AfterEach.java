package no.testframework.javalibrary.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to be executed <b>after each individual test</b> in the suite.
 *
 * <p>The annotated method is invoked on the same instance that ran the test,
 * and is <em>always</em> called — even when the test failed or timed out.
 * Use it to clean up per-test state (closing connections, resetting mocks, etc.).
 *
 * <p>In parallel mode, {@code @AfterEach} methods for different tests run
 * concurrently on their respective instances.
 *
 * <p>Requirements:
 * <ul>
 *   <li>Must be {@code public} and have no parameters.</li>
 *   <li>Guaranteed to run after the test body, regardless of outcome.</li>
 * </ul>
 *
 * @see BeforeEach
 * @see AfterAll
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AfterEach {

    /**
     * Execution order among methods of the same lifecycle phase.
     *
     * <p>Lower values run first. Methods with the same order are executed in method-name
     * order to keep execution deterministic across JVMs.
     */
    int order() default 0;
}
