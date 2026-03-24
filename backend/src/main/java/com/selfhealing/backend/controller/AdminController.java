package com.selfhealing.backend.Controller;

import com.selfhealing.backend.repository.FailureMetricRepository;
import com.selfhealing.backend.service.CircuitBreakerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
public class AdminController {
    @Autowired
    CircuitBreakerService circuitBreakerService;
    @Autowired
    FailureMetricRepository failureMetricRepository;
    @Autowired
    SimpMessagingTemplate messagingTemplate;


    @GetMapping("/admin/alert-status")
    public String getAlertStatus(){
        long totalFailures = failureMetricRepository.count();
        if(totalFailures >= 5){
            messagingTemplate.convertAndSend("/topic/alerts", "CRITICAL");
        } else if(totalFailures >= 3){
            messagingTemplate.convertAndSend("/topic/alerts", "CRITICAL");
        }
        return "NORMAL";
    }

    @PostMapping("/admin/reset-circuit")
    public String resetCircuit(){
        circuitBreakerService.resetCircuit();
        messagingTemplate.convertAndSend("/topic/metrics", "update");
        return "Circuit breaker reset to CLOSED state";
    }

    @PostMapping("/admin/clear-metrics")
    public String clearMetrics(){

        failureMetricRepository.deleteAll();
        messagingTemplate.convertAndSend("/topic/metrics", "update");

        return "Failure metrics cleared";
    }
}
