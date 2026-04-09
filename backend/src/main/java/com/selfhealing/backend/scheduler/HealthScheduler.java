package com.selfhealing.backend.scheduler;

import com.selfhealing.backend.model.HealthMetric;
import com.selfhealing.backend.model.SystemHealthStatus;
import com.selfhealing.backend.repository.HealthMetricRepository;
import com.selfhealing.backend.service.SystemHealthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HealthScheduler {

    @Autowired
    private SystemHealthService healthService;

    @Autowired
    private HealthMetricRepository repo;

    @Scheduled(fixedRate = 5000) // every 5 sec
    public void captureHealth() {

        int score = healthService.calculateHealthScore();
        SystemHealthStatus status = healthService.getHealthStatus(score);

        repo.save(new HealthMetric(score, status));
    }
}
