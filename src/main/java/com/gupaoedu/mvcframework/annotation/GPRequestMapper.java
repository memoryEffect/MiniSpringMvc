package com.gupaoedu.mvcframework.annotation;

import java.lang.annotation.*;

/**
 * Created by Administrator on 2019/3/29.
 */
@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GPRequestMapper {
    String value() default "";
}
