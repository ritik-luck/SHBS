package com.selfhealing.backend.Controller;

import com.selfhealing.backend.service.CircuitBreakerService;
import com.selfhealing.backend.service.FailureMetricService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
public class SystemHealthController {

    @Autowired
    FailureMetricService failureMetricService;
    @Autowired
    CircuitBreakerService circuitBreakerService;

    @GetMapping("/simulate-failure")
    public String simulateFailure() {
        if(!circuitBreakerService.allowRequest()){
            return circuitBreakerService.fallbackResponse();
        }
        return failureMetricService.recordFailure();
    }
}