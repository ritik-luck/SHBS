package com.selfhealing.backend.service;

import com.selfhealing.backend.model.*;
import org.springframework.stereotype.Service;

@Service
public class FailureClassifierService {

    public FailureCategory classifyCategory(String type) {

        switch (type.toLowerCase()) {
            case "timeout": return FailureCategory.TIMEOUT;
            case "database": return FailureCategory.DATABASE;
            case "network": return FailureCategory.NETWORK;
            case "rate_limit": return FailureCategory.RATE_LIMIT;
            default: return FailureCategory.EXCEPTION;
        }
    }

    public FailureSeverity classifySeverity(long failureCount) {

        if (failureCount >= 5) return FailureSeverity.CRITICAL;
        if (failureCount >= 3) return FailureSeverity.HIGH;
        if (failureCount >= 1) return FailureSeverity.MEDIUM;

        return FailureSeverity.LOW;
    }
}