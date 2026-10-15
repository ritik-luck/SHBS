package com.selfhealing.backend.scheduler;

import com.selfhealing.backend.model.HealthMetric;
import com.selfhealing.backend.model.SystemHealthStatus;
import com.selfhealing.backend.repository.HealthMetricRepository;
import com.selfhealing.backend.service.SystemHealthService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HealthScheduler {

    private final SystemHealthService healthService;
    private final HealthMetricRepository repo;

    public HealthScheduler(SystemHealthService healthService,
                           HealthMetricRepository repo) {
        this.healthService = healthService;
        this.repo = repo;
    }

    @Scheduled(fixedRate = 5000) 
    public void captureHealth() {

        int score = healthService.calculateHealthScore();
        SystemHealthStatus status = healthService.getHealthStatus(score);

        repo.save(new HealthMetric(score, status));
    }
}
