package com.selfhealing.backend.service;

import com.selfhealing.backend.model.AuditLog;
import com.selfhealing.backend.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    @Autowired
    AuditLogRepository repository;

    public void log(String action, String details, String triggeredBy, String result) {

        AuditLog log = new AuditLog(action, details, triggeredBy, result);

        repository.save(log);
    }
}