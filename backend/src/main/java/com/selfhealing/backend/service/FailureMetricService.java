package com.selfhealing.backend.service;

import com.selfhealing.backend.model.FailureCategory;
import com.selfhealing.backend.model.FailureMetric;
import com.selfhealing.backend.model.FailureSeverity;
import com.selfhealing.backend.model.HealingStrategy;
import com.selfhealing.backend.repository.FailureMetricRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Service
public class FailureMetricService {
    @Autowired
    FailureMetricRepository repository;
    @Autowired
    CircuitBreakerService circuitBreakerService;
    @Autowired
    SimpMessagingTemplate messagingTemplate;
    @Autowired
    FailureClassifierService classifier;
    @Autowired
    AuditService auditService;
    @Autowired
    HealingStrategyEngine healingEngine;


    public String recordFailure(String type, String serviceName) {

        long count = repository.count();
        HealingStrategy strategy = healingEngine.decideStrategy(count);
        System.out.println("Strategy decided: " + strategy);

        FailureCategory category = classifier.classifyCategory(type);
        FailureSeverity severity = classifier.classifySeverity(count);

        FailureMetric metric = new FailureMetric(
                serviceName,
                type,
                category,
                severity
        );

        repository.save(metric);

        auditService.log(
                "FAILURE_RECORDED",
                "Type: " + type + ", Service: " + serviceName,
                "SYSTEM",
                "SUCCESS"
        );

        circuitBreakerService.recordFailure(count);

        messagingTemplate.convertAndSend("/topic/metrics", "update");

        return "Failure recorded: " + type + " [" + severity + "]";

    }
}