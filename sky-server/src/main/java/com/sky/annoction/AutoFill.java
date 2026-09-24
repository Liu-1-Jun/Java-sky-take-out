package com.sky.annoction;

import com.sky.enumeration.OperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.FIELD})//表示这个注解可以用于方法和字段上
@Retention(RetentionPolicy.RUNTIME)//表示这个注解在运行时仍然有效
public @interface AutoFill {

    OperationType value();//标注这个注解的元素所属的操作类型
}
