package com.selfhealing.backend.controller;

import com.selfhealing.backend.dto.FailureRequest;
import com.selfhealing.backend.service.CircuitBreakerService;
import com.selfhealing.backend.service.FailureMetricService;
import com.selfhealing.backend.service.SystemHealthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
public class SystemHealthController {

    @Autowired
    FailureMetricService failureMetricService;
    @Autowired
    CircuitBreakerService circuitBreakerService;
    @Autowired
    SystemHealthService healthService;

        @GetMapping("/health")
        public Map<String, Object> getHealth() {

            Map<String, Object> response = new HashMap<>();

            response.put("score", healthService.calculateHealthScore());
            response.put("status", healthService.getHealthStatus());

            return response;
        }

    @PostMapping("/simulate-failure")
    public String simulateFailure(@RequestBody FailureRequest body) {
        return failureMetricService.recordFailure(body.type , "Api -Service");

    }
}