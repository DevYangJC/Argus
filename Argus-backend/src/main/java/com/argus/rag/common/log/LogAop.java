package com.argus.rag.common.log;

import com.argus.rag.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 操作日志 AOP 切面，记录被 {@link OperationLog} 标记的方法的入参、耗时和返回值。
 */
@Slf4j
@Component
@Aspect
@Order(1)
public class LogAop {

    @Pointcut("@within(com.argus.rag.common.log.OperationLog) || @annotation(com.argus.rag.common.log.OperationLog)")
    public void logAdvicePointcut() {
    }

    @Around("logAdvicePointcut()")
    public Object logAroundAdvice(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String args = Arrays.toString(joinPoint.getArgs());

        long startTime = System.currentTimeMillis();
        log.info("--> [{}] {}({})", className, methodName, args);

        try {
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - startTime;
            logResult(className, methodName, elapsed, result);
            return result;
        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("<-- [{}] {} FAIL [{}ms] {}", className, methodName, elapsed, e.toString());
            throw e;
        }
    }

    private void logResult(String className, String methodName, long elapsed, Object result) {
        if (result instanceof ApiResponse<?> apiResponse) {
            log.info("<-- [{}] {} [{}ms] success={} {}",
                    className, methodName, elapsed,
                    apiResponse.isSuccess(),
                    apiResponse.getMessage() != null ? apiResponse.getMessage() : "");
        } else {
            log.info("<-- [{}] {} [{}ms]", className, methodName, elapsed);
        }
    }
}
