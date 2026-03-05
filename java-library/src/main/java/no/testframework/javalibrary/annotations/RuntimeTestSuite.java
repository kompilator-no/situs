package no.testframework.javalibrary.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RuntimeTestSuite {
    String name() default "";
    String description() default "";
    /**
     * When {@code true} all tests in this suite are executed in parallel.
     * When {@code false} (default) tests run sequentially in declaration order.
     */
    boolean parallel() default false;
}