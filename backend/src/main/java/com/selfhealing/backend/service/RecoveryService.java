package com.selfhealing.backend.service;

import com.selfhealing.backend.model.CircuitBreakerState;
import com.selfhealing.backend.model.RecoveryMetric;
import com.selfhealing.backend.repository.RecoveryMetricRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RecoveryService {

    @Autowired
    private CircuitBreakerService circuitBreakerService;
    @Autowired
    ConfigService configService;
    @Autowired
    private AuditService auditService;
    @Autowired
    RecoveryMetricRepository recoveryRepo;
    @Autowired
    AlertService alertService;


    private int successCount = 0;
    private int retryAttempts = 0;

    public void attemptRecovery() {
        long start = System.currentTimeMillis();

        try {
            Thread.sleep(backoffTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (circuitBreakerService.getState() != CircuitBreakerState.OPEN) {
            return;
        }
        System.out.println("🔄 Recovery attempt started...");
        auditService.log(
                "RECOVERY_ATTEMPT",
                "Trying recovery attempt #" + retryAttempts,
                "SYSTEM",
                "STARTED"
        );

        // 🔹 Stage 1: Health Check
        boolean healthy = performHealthCheck();

        recoveryRepo.save(new RecoveryMetric(
                "HEALTH_CHECK",
                healthy ? "SUCCESS" : "FAILURE",
                System.currentTimeMillis() - start,
                "Health check stage"
        ));

        if (!healthy) {
            retryAttempts++;
            auditService.log("RECOVERY_FAILED", "Health check failed", "SYSTEM", "FAILURE");
            alertService.sendAlert("WARNING", "Recovery attempt failed", "RECOVERY");
            increaseBackoff();
            return;
        }

        // 🔹 Stage 2: Canary Request
        boolean testSuccess = performTestRequest();

        recoveryRepo.save(new RecoveryMetric(
                "TEST_REQUEST",
                testSuccess ? "SUCCESS" : "FAILURE",
                System.currentTimeMillis() - start,
                "Canary test"
        ));

        if (!testSuccess) {
            retryAttempts++;
            auditService.log("RECOVERY_FAILED", "Test request failed", "SYSTEM", "FAILURE");
            increaseBackoff();
            return;
        }

        // 🔹 Stage 3: Move to HALF_OPEN
        circuitBreakerService.tryRecovery();

        successCount++;

        // 🔹 Stage 4: Full Restore
        int maxSuccessThreshold = configService.getInt("recovery_success_threshold", 3);
        if (successCount >= maxSuccessThreshold) {
            circuitBreakerService.resetCircuit();

            recoveryRepo.save(new RecoveryMetric(
                    "FULL_RECOVERY",
                    "SUCCESS",
                    System.currentTimeMillis() - start,
                    "System restored"
            ));

            auditService.log(
                    "RECOVERY_SUCCESS",
                    "System fully recovered",
                    "SYSTEM",
                    "SUCCESS"
            );

            successCount = 0;
            retryAttempts = 0;
            resetBackoff();
        }
    }

    // 🔹 Simulated health check
    private boolean performHealthCheck() {
        System.out.println("Health check passed");
        return true;
    }

    // 🔹 Simulated test request
    private boolean performTestRequest() {
        System.out.println("Test request success");
        return true;
    }

    private long backoffTime = 2000;

    private void increaseBackoff() {
        backoffTime = Math.min(backoffTime * 2, 30000); // max 30s
    }

    private void resetBackoff() {
        backoffTime = 2000;
    }
}