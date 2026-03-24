package com.selfhealing.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SystemHealthService {

    private static final Logger logger =
            LoggerFactory.getLogger(SystemHealthService.class);

    public String getHealthStatus() {
        logger.info("Health endpoint accessed");
        return "UP";
    }
}