package com.selfhealing.backend.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RequestLoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingAspect.class);

    @Around("execution(* com.selfhealing.backend.controller..*(..))")
    public Object logRequest(ProceedingJoinPoint joinPoint) throws Throwable {

        long startTime = System.currentTimeMillis();

        String methodName = joinPoint.getSignature().toShortString();

        logger.info("➡️ Incoming request: {}", methodName);

        Object result;

        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            logger.error("❌ Exception in {}: {}", methodName, e.getMessage());
            throw e;
        }

        long timeTaken = System.currentTimeMillis() - startTime;

        logger.info("✅ Completed: {} in {} ms", methodName, timeTaken);

        return result;
    }
}