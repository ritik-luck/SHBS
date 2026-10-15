package com.selfhealing.backend.controller;

import com.selfhealing.backend.service.CircuitBreakerService;
import com.selfhealing.backend.service.DegradedResponseService;
import com.selfhealing.backend.service.FailureMetricService;
import com.selfhealing.backend.service.FallbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

import java.util.Map;

@RestController
public class HelloController {

    private final DegradedResponseService degradedService;
    private final CircuitBreakerService circuitBreakerService;
    private final FallbackService fallbackService;
    private final FailureMetricService failureMetricService;

    public HelloController(DegradedResponseService degradedService,
                           CircuitBreakerService circuitBreakerService,
                           FallbackService fallbackService,
                           FailureMetricService failureMetricService) {
        this.degradedService = degradedService;
        this.circuitBreakerService = circuitBreakerService;
        this.fallbackService = fallbackService;
        this.failureMetricService = failureMetricService;
    }


    @RateLimiter(name = "apiLimiter", fallbackMethod = "rateLimitFallback")
    @GetMapping("/hello")
    public ResponseEntity<?> hello() {

        String endpoint = "/hello";

        if (!circuitBreakerService.allowRequest()) {

            if (degradedService.hasCache(endpoint)) {

                return ResponseEntity
                        .status(200)
                        .header("X-Degraded", "true")
                        .body(degradedService.getDegradedResponse(endpoint, "Circuit breaker OPEN"));
            }

            return ResponseEntity //ResponseEntity is a class in Spring Boot used to build full HTTP responses.
                    .status(503)
                    .header("Retry-After", "5")
                    .body(fallbackService.getFallback(endpoint));
        }

        String response = "Hello, system is running!";
        degradedService.cacheResponse(endpoint, response);

        return ResponseEntity.ok(response);
    }

    public ResponseEntity<?> rateLimitFallback(Throwable t) {
        failureMetricService.recordFailure("rate_limit", "api-service");

        return ResponseEntity
                .status(429)
                .header("Retry-After", "10")
                .body(Map.of(
                        "error", "Too many requests",
                        "message", "Rate limit exceeded",
                        "timestamp", System.currentTimeMillis()
                ));
    }
}