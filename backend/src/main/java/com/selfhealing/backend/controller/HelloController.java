package com.selfhealing.backend.controller;

import com.selfhealing.backend.service.FailureMetricService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

@RestController
public class HelloController {

    @RateLimiter(name = "apiLimiter", fallbackMethod = "rateLimitFallback")
    @GetMapping("/hello")
    public String hello() {
        return "Hello, system is running!";
    }

    @Autowired
    FailureMetricService failureMetricService;

    public String rateLimitFallback(Exception e) {
        failureMetricService.recordFailure("rate_limit", "api-service");
        return "Too many requests! Try again later.";
    }
}