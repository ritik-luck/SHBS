package com.selfhealing.backend.service;

import com.selfhealing.backend.model.SystemHealthStatus;
import com.selfhealing.backend.repository.FailureMetricRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SystemHealthService {

    private static final Logger log = LoggerFactory.getLogger(SystemHealthService.class);

    private final FailureMetricRepository failureMetricRepository;
    private final CircuitBreakerService circuitBreakerService;
    private final AlertService alertService;

    public SystemHealthService(FailureMetricRepository failureMetricRepository,
                               CircuitBreakerService circuitBreakerService,
                               AlertService alertService) {
        this.failureMetricRepository = failureMetricRepository;
        this.circuitBreakerService = circuitBreakerService;
        this.alertService = alertService;
    }

    public SystemHealthStatus getHealthStatus(int score) {

        if (score >= 80) return SystemHealthStatus.HEALTHY;
        if (score < 30) {
            alertService.sendAlert(
                    "CRITICAL",
                    "System health is UNHEALTHY: " + score,
                    "HEALTH"
            );
        }
        if (score >= 40) return SystemHealthStatus.DEGRADED;

        return SystemHealthStatus.UNHEALTHY;
    }

    public int calculateHealthScore() {

        long failures = failureMetricRepository.count();
        String state = circuitBreakerService.getState().toString();

        int score = 100;

        
        score -= failures * 5;


        if (state.equals("OPEN")) score -= 40;
        else if (state.equals("HALF_OPEN")) score -= 20;

        return Math.max(score, 0);
    }

}