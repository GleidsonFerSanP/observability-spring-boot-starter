package com.empresa.platform.observability.core.annotation;
import java.lang.annotation.*;
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface LogLeg {
    String target();
    boolean includePayload() default false;
}
