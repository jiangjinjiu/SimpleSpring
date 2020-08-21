package com.example.annotation;

import javax.annotation.Resource;
import java.lang.annotation.*;

/**
 * @author jiang
 */
@Target(ElementType.TYPE)
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface DService {
    String value() default "";
}
