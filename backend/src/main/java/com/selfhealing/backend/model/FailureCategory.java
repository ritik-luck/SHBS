package com.selfhealing.backend.model;

public enum FailureCategory {
    TIMEOUT,
    EXCEPTION,
    DATABASE,
    NETWORK,
    DEPENDENCY,
    RATE_LIMIT
}
