package com.selfhealing.backend.scheduler;

import com.selfhealing.backend.service.RecoveryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecoveryScheduler {

    private final RecoveryService recoveryService;

    public RecoveryScheduler(RecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @Scheduled(fixedRate = 5000)
    public void runRecovery(){
        // Monitor all downstream dependencies
        recoveryService.attemptRecovery("inventory-service");
        recoveryService.attemptRecovery("payment-service");
        recoveryService.attemptRecovery("notification-service");
    }
}
