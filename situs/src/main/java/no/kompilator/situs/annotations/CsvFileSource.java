package no.kompilator.situs.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Supplies one invocation per CSV row loaded from one or more classpath resources.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CsvFileSource {

    /**
     * Classpath resource paths to read. Leading {@code /} is optional.
     */
    String[] resources();

    char delimiter() default ',';

    /**
     * Number of initial lines to skip in each resource, typically used for headers.
     */
    int numLinesToSkip() default 0;

    String encoding() default "UTF-8";
}
