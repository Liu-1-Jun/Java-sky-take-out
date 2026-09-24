package com.sky.aop;


import com.sky.annoction.AutoFill;
import com.sky.constant.AutoFillConstant;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

@Slf4j
@Aspect//表示这是一个切面类，切面类要交给Spring管理
@Component
public class AutoFillAop {
    //切入点
    @Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.annoction.AutoFill)")//切入点表达式，表示匹配com.sky.mapper包下的所有方法
    public void pointcut() {
    }

    @Before("pointcut()")
    public void autoFill(JoinPoint joinPoint) {
        log.info("执行了自动填充 {}", joinPoint);
        // 获取方法签名，并且强转为MethodSignature
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        // 获取方法
        Method method = methodSignature.getMethod();
        //获取方法上的AutoFill注解
        AutoFill autoFill = method.getAnnotation(AutoFill.class);
        // 判断autoFill注解是否存在
        if (autoFill == null || autoFill.value() == null) {
            return;
        }
        Object[] args = joinPoint.getArgs();
        // 判断args数组是否存在元素,如果不存在则返回，不进行后续处理
        if (args == null || args.length == 0) {
            return;
        }
        // 获取方法参数列表的第一个参数，其实也就是需要填充的实体对象
        Object arg = args[0];
        // 判断当前方法是否是INSERT操作
        if (autoFill.value() == OperationType.INSERT) {
            // 获取实体类的方法并且通过反射调用
            try {
                Method setCreateTime = arg.getClass().getMethod(AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class);
                Method setUpdateTime = arg.getClass().getMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                Method setCreateUser = arg.getClass().getMethod(AutoFillConstant.SET_CREATE_USER, Long.class);
                Method setUpdateUser = arg.getClass().getMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);
                // 通过反射调用方法，设置创建时间和更新时间
                setCreateTime.invoke(arg, LocalDateTime.now());
                setUpdateTime.invoke(arg, LocalDateTime.now());
                setCreateUser.invoke(arg, BaseContext.getCurrentId());
                setUpdateUser.invoke(arg, BaseContext.getCurrentId());
                log.info("为插入操作自动填充字段");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else if (autoFill.value() == OperationType.UPDATE) {// 判断当前方法是否是UPDATE操作
            try {
                Method setUpdateTime = arg.getClass().getMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                Method setUpdateUser = arg.getClass().getMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);
                setUpdateTime.invoke(arg, LocalDateTime.now());
                setUpdateUser.invoke(arg, BaseContext.getCurrentId());
                log.info("为更新操作自动填充字段");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return;
    }
}
