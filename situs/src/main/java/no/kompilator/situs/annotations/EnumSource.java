package no.kompilator.situs.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Supplies enum constants to a single-argument {@link ParameterizedTest}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EnumSource {

    Class<? extends Enum<?>> value();

    String[] names() default {};

    Mode mode() default Mode.INCLUDE;

    enum Mode {
        INCLUDE,
        EXCLUDE
    }
}
