package com.selfhealing.backend.scheduler;

import com.selfhealing.backend.service.CircuitBreakerService;
import com.selfhealing.backend.service.RecoveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecoveryScheduler {
    @Autowired
    RecoveryService recoveryService;

    @Scheduled(fixedRate = 5000)
    public void runRecovery(){
        recoveryService.attemptRecovery();
    }
}
