package com.selfhealing.backend.service;

import com.selfhealing.backend.model.SystemHealthStatus;
import com.selfhealing.backend.repository.FailureMetricRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SystemHealthService {

    @Autowired
    FailureMetricRepository failureMetricRepository;
    @Autowired
    CircuitBreakerService circuitBreakerService;
    @Autowired
    AlertService alertService;

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

        // 🔻 Reduce score based on failures
        score -= failures * 5;

        // 🔻 Circuit breaker impact
        if (state.equals("OPEN")) score -= 40;
        else if (state.equals("HALF_OPEN")) score -= 20;

        return Math.max(score, 0);
    }

}