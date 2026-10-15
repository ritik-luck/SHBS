package com.selfhealing.backend.service;

import com.selfhealing.backend.model.AuditLog;
import com.selfhealing.backend.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void log(String action, String details, String triggeredBy, String result) {
        AuditLog log = new AuditLog(action, details, triggeredBy, result);
        repository.save(log);
    }

    public void logCompliance(String action, String details, String triggeredBy, String result,
                              String actor, String requestId, String ipAddress, 
                              String targetResource, String reason, String beforeAfterState) {
        AuditLog log = new AuditLog(action, details, triggeredBy, result, actor, requestId, ipAddress, targetResource, reason, beforeAfterState);
        repository.save(log);
    }
}