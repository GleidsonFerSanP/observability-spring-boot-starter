package com.empresa.platform.observability.core.annotation;
import java.lang.annotation.*;
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ObservationTags.class)
public @interface ObservationTag {
    String key();
    String expression();
    boolean highCardinality() default false;
}
