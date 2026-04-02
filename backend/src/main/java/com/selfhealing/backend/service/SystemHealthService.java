package com.selfhealing.backend.service;

import com.selfhealing.backend.model.SystemHealthStatus;
import com.selfhealing.backend.repository.FailureMetricRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SystemHealthService {

    @Autowired
    private FailureMetricRepository repository;

    public int calculateHealthScore() {

        long failures = repository.count();

        int score = 100 - (int)(failures * 10);

        return Math.max(score, 0);
    }

    public SystemHealthStatus getHealthStatus() {

        int score = calculateHealthScore();

        if (score >= 80) return SystemHealthStatus.HEALTHY;
        if (score >= 40) return SystemHealthStatus.DEGRADED;

        return SystemHealthStatus.UNHEALTHY;
    }
}