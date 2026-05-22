package com.nageoffer.ai.ragent.core.datasource;

import cn.hutool.core.util.StrUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnProperty(prefix = "ragent.datasource.dynamic", name = "enabled", havingValue = "true")
public class UseDataSourceAspect {

    @Around("@annotation(com.nageoffer.ai.ragent.core.datasource.UseDataSource) "
            + "|| @within(com.nageoffer.ai.ragent.core.datasource.UseDataSource)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        UseDataSource annotation = resolveAnnotation(joinPoint);
        if (annotation == null || StrUtil.isBlank(annotation.value())) {
            return joinPoint.proceed();
        }
        RoutingDataSourceContext.push(annotation.value());
        try {
            return joinPoint.proceed();
        } finally {
            RoutingDataSourceContext.pop();
        }
    }

    private UseDataSource resolveAnnotation(ProceedingJoinPoint joinPoint) {
        if (!(joinPoint.getSignature() instanceof MethodSignature methodSignature)) {
            return null;
        }
        Method method = methodSignature.getMethod();
        Class<?> targetClass = joinPoint.getTarget() == null ? method.getDeclaringClass() : joinPoint.getTarget().getClass();
        Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);
        UseDataSource annotation = AnnotationUtils.findAnnotation(specificMethod, UseDataSource.class);
        if (annotation != null) {
            return annotation;
        }
        return AnnotationUtils.findAnnotation(targetClass, UseDataSource.class);
    }
}
