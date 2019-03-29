package com.gupaoedu.mvcframework.annotation;

import java.lang.annotation.*;

/**
 * Created by Administrator on 2019/3/29.
 */
//源注解
@Target({ElementType.FIELD})
//生命周期
@Retention(RetentionPolicy.RUNTIME)
//这个注解的说明
@Documented
public @interface GPAutowired {
    String value() default "";
}
