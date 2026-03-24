package com.selfhealing.backend.Controller;

import com.selfhealing.backend.service.StatusService;
import com.selfhealing.backend.model.FailureMetric;
import com.selfhealing.backend.model.SystemStatusResponse;
import com.selfhealing.backend.repository.FailureMetricRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @Autowired
    private StatusService statusService;
    @Autowired
    private FailureMetricRepository repository;

    @GetMapping("/status")
    public SystemStatusResponse getStatus() {

        if (Math.random() > 0.5) {
            FailureMetric metric = new FailureMetric(1 , "auth-service" , "timeout");
            repository.save(metric);
            throw new RuntimeException("Random failure occurred");
        }

        return new SystemStatusResponse(
                "UP",
                "System is running normally"
        );
    }
}