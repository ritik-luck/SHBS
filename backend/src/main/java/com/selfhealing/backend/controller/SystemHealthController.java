package com.selfhealing.backend.controller;

import com.selfhealing.backend.dto.FailureRequest;
import com.selfhealing.backend.model.HealthMetric;
import com.selfhealing.backend.model.SystemHealthStatus;
import com.selfhealing.backend.repository.HealthMetricRepository;
import com.selfhealing.backend.service.CircuitBreakerService;
import com.selfhealing.backend.service.FailureMetricService;
import com.selfhealing.backend.service.SystemHealthService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@CrossOrigin(origins = "${cors.allowed-origins}")
@RestController
public class SystemHealthController {

    private final FailureMetricService failureMetricService;
    private final CircuitBreakerService circuitBreakerService;
    private final SystemHealthService healthService;
    private final HealthMetricRepository healthMetricRepository;

    public SystemHealthController(FailureMetricService failureMetricService,
                                  CircuitBreakerService circuitBreakerService,
                                  SystemHealthService healthService,
                                  HealthMetricRepository healthMetricRepository) {
        this.failureMetricService = failureMetricService;
        this.circuitBreakerService = circuitBreakerService;
        this.healthService = healthService;
        this.healthMetricRepository = healthMetricRepository;
    }

        @GetMapping("/health")
        public Map<String, Object> getHealth() {
            Map<String, Object> response = new HashMap<>();
            int score = healthService.calculateHealthScore();
            SystemHealthStatus status = healthService.getHealthStatus(score);

            return Map.of(
                    "score", score,
                    "status", status,
                    "timestamp", System.currentTimeMillis()
            );
        }

    @PostMapping("/simulate-failure")
    public String simulateFailure(@RequestBody FailureRequest body) {
        return failureMetricService.recordFailure(body.type , "Api -Service");

    }

    @GetMapping("/health/history")
    public Page<HealthMetric> history(Pageable pageable) {
        return healthMetricRepository.findAll(pageable);
    }
}