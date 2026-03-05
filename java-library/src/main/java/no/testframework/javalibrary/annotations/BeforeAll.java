package no.testframework.javalibrary.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to be executed <b>once before all tests</b> in the suite start.
 *
 * <p>The annotated method is invoked on a shared suite instance before any
 * {@code @RunTimeTest} method runs. Use it to set up expensive resources that
 * are shared across all tests (e.g. starting a server, loading fixtures).
 *
 * <p>Requirements:
 * <ul>
 *   <li>Must be {@code public} and have no parameters.</li>
 *   <li>If the method throws, the entire suite is aborted.</li>
 * </ul>
 *
 * @see AfterAll
 * @see BeforeEach
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BeforeAll {
}
