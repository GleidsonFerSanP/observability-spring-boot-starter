package com.empresa.platform.observability.core.annotation;
import java.lang.annotation.*;
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface TrackFlow {
    String value() default "";
}
