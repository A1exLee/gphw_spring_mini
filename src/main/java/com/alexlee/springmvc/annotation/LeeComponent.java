package com.alexlee.springmvc.annotation;


import java.lang.annotation.*;

@Target({ElementType.TYPE,ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface LeeComponent {
    String value() default "";
}
