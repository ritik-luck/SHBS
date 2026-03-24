package com.selfhealing.backend.scheduler;

import com.selfhealing.backend.service.CircuitBreakerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecoveryScheduler {
    @Autowired
    CircuitBreakerService circuitBreakerService;

    @Scheduled(fixedRate = 1000)
    public void checkRecovery(){
        circuitBreakerService.tryRecovery();
    }
}
