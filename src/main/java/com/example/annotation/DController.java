package com.example.annotation;

import java.lang.annotation.*;

/**
 * @author : fayne
 * @date : 2020-08-18
 **/
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DController {
    String value() default "";
}
