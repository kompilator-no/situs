package no.testframework.javalibrary.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RunTimeTest {
    String name() default "";
    String description() default "";
    /**
     * Maximum allowed execution time in milliseconds.
     * 0 means "use the framework default" (10 000 ms).
     * Set to -1 to explicitly disable the timeout.
     */
    long timeoutMs() default 0;
    /**
     * Milliseconds to wait before starting this test.
     * Useful for staggering tests or waiting for external systems to be ready.
     * 0 means no delay (default).
     */
    long delayMs() default 0;
}
