package com.selfhealing.backend.controller;

import com.selfhealing.backend.model.RecoveryMetric;
import com.selfhealing.backend.repository.FailureMetricRepository;
import com.selfhealing.backend.repository.RecoveryMetricRepository;
import com.selfhealing.backend.service.CircuitBreakerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
public class MetricsController {
    @Autowired
    FailureMetricRepository failureMetricRepository;
    @Autowired
    CircuitBreakerService circuitBreakerService;
    @Autowired
    RecoveryMetricRepository repo;


    @GetMapping("/metrics/failures")
    public long totalFailures(){
        return failureMetricRepository.count();
    }


    @GetMapping("/metrics/failures/services")
    public List<Object[]> failuresByService(){
        return failureMetricRepository.countFailuresByService();
    }

    @GetMapping("/metrics/failures/type")
    public List<Object[]> byType() {
        return failureMetricRepository.countFailuresByType();
    }

    @GetMapping("/metrics/failures/severity")
    public List<Object[]> bySeverity() {
        return failureMetricRepository.countFailuresBySeverity();
    }

    @GetMapping("/metrics/failures/timeline")
    public List<Object[]> timeline(@RequestParam(defaultValue = "1h") String period) {

        LocalDateTime start;

        switch (period) {
            case "24h":
                start = LocalDateTime.now().minusHours(24);
                break;
            case "30m":
                start = LocalDateTime.now().minusMinutes(30);
                break;
            default:
                start = LocalDateTime.now().minusHours(1);
        }

        return failureMetricRepository.countFailuresTimeline(start);
    }

    @GetMapping("/system/state")
    public String systemState(){
        return circuitBreakerService.getState().toString();
    }

    @GetMapping("/metrics/recovery")
    public List<RecoveryMetric> getRecoveryMetrics() {
        return repo.findAll();
    }

    @GetMapping("/metrics/recovery/summary")
    public Map<String, Object> summary() {

        List<RecoveryMetric> all = repo.findAll();

        long totalAttempts = all.stream()
                .filter(m -> m.getStage().equals("FULL_RECOVERY"))
                .count();

        long success = all.stream()
                .filter(m -> m.getStage().equals("FULL_RECOVERY")
                        && m.getResult().equals("SUCCESS"))
                .count();

        double successRate = totalAttempts == 0 ? 0 :
                (success * 100.0 / totalAttempts);

        return Map.of(
                "totalAttempts", totalAttempts,
                "successCount", success,
                "successRate", successRate
        );
    }
}
