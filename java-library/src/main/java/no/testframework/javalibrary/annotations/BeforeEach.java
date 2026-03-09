package no.testframework.javalibrary.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to be executed <b>before each individual test</b> in the suite.
 *
 * <p>The annotated method is invoked on the same fresh instance that will run
 * the test. In parallel mode, each test gets its own instance, so
 * {@code @BeforeEach} methods run concurrently — they must not rely on shared
 * mutable state.
 *
 * <p>Requirements:
 * <ul>
 *   <li>Must be {@code public} and have no parameters.</li>
 *   <li>If the method throws, the test is skipped and the suite run is aborted.</li>
 * </ul>
 *
 * @see AfterEach
 * @see BeforeAll
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BeforeEach {

    /**
     * Execution order among methods of the same lifecycle phase.
     *
     * <p>Lower values run first. Methods with the same order are executed in method-name
     * order to keep execution deterministic across JVMs.
     */
    int order() default 0;
}
