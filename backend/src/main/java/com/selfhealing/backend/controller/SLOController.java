package com.selfhealing.backend.controller;

import com.selfhealing.backend.service.SLOService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@CrossOrigin(origins = "${cors.allowed-origins}")
@RestController
public class SLOController {

    private final SLOService sloService;

    public SLOController(SLOService sloService) {
        this.sloService = sloService;
    }

    @GetMapping("/metrics/slo")
    public Map<String, Object> getSLOMetrics() {
        return sloService.getMetrics();
    }
}
