package com.selfhealing.backend.Service;

import com.selfhealing.backend.model.SystemStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


@Service
public class StatusService {

    private static final Logger log =
            LoggerFactory.getLogger(StatusService.class);

    public SystemStatusResponse getStatus() {
        log.info("Status API called");

        if (Math.random() > 0.5) {
            log.error("Random failure occurred");
            throw new RuntimeException("Random failure occurred");
        }
        log.info("Status API success");
        return new SystemStatusResponse(
                "UP",
                "System is running normally"
        );
    }
}