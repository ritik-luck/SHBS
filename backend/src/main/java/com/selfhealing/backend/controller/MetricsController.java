package com.selfhealing.backend.Controller;

import com.selfhealing.backend.repository.FailureMetricRepository;
import com.selfhealing.backend.service.CircuitBreakerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
public class MetricsController {
    @Autowired
    FailureMetricRepository failureMetricRepository;
    @Autowired
    CircuitBreakerService circuitBreakerService;

    @GetMapping("/metrics/failures")
    public long totalFailures(){
        return failureMetricRepository.count();
    }

    @GetMapping("/metrics/services")
    public List<Object[]> failuresByService(){
        return failureMetricRepository.countFailuresByService();
    }

    @GetMapping("/system/state")
    public String systemState(){
        return circuitBreakerService.getState().toString();
    }
}
