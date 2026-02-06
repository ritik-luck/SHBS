package com.selfhealing.backend.model;

public class SystemStatusResponse {

    private String status;
    private String message;

    public SystemStatusResponse(String status, String message) {
        this.status = status;
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}