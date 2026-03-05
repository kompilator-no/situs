package no.testframework.javalibrary.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to be executed <b>once after all tests</b> in the suite have finished.
 *
 * <p>The annotated method is invoked on a shared suite instance after every
 * {@code @RunTimeTest} method (and its {@code @AfterEach}) has completed.
 * Use it to tear down expensive resources opened in {@code @BeforeAll}.
 *
 * <p>Requirements:
 * <ul>
 *   <li>Must be {@code public} and have no parameters.</li>
 *   <li>Runs even if some tests failed, but not if {@code @BeforeAll} threw.</li>
 * </ul>
 *
 * @see BeforeAll
 * @see AfterEach
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AfterAll {
}
