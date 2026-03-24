package com.selfhealing.backend.service;

import com.selfhealing.backend.model.FailureMetric;
import com.selfhealing.backend.repository.FailureMetricRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Service
public class FailureMetricService {

    private static final int FAILURE_THRESHOLD = 3;
    @Autowired
    FailureMetricRepository repository;
    @Autowired
    CircuitBreakerService circuitBreakerService;
    @Autowired
    SimpMessagingTemplate messagingTemplate;


    public String recordFailure() {
        FailureMetric metric = new FailureMetric(1 , "auth-service" , "timeout");
        repository.save(metric);

        long totalFailures = repository.count();
        circuitBreakerService.recordFailure(totalFailures);

        messagingTemplate.convertAndSend("/topic/metrics", "update");

        return "Failure recorded";
    }
}