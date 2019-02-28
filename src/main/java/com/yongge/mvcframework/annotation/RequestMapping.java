package com.yongge.mvcframework.annotation;

import java.lang.annotation.*;

//ElementType.TYPE 注解可以在类上
//ElementType.METHOD 注解可以在方法上
@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestMapping {
    String value() default "";
}
