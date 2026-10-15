package com.selfhealing.backend.config;

import com.selfhealing.backend.service.SLOService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * RequestMetricsInterceptor — Automatically records every HTTP request.
 *
 * This is the critical missing piece that connects real traffic to the SLO engine.
 * Before this interceptor, SLO tracking had to be manually wired into each controller.
 * Now, every request is automatically measured for: endpoint, method, status, latency,
 * and success/failure — feeding the SLO burn rate calculator in real time.
 */
@Component
public class RequestMetricsInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestMetricsInterceptor.class);
    private static final String START_TIME_ATTR = "shbs.request.startTime";

    private final SLOService sloService;
    private final MeterRegistry meterRegistry;

    public RequestMetricsInterceptor(SLOService sloService, MeterRegistry meterRegistry) {
        this.sloService = sloService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        String uri = request.getRequestURI();

        // Skip actuator, WebSocket, and static resource endpoints
        if (uri.startsWith("/actuator") || uri.startsWith("/ws") || uri.contains(".")) {
            return;
        }

        int statusCode = response.getStatus();
        String method = request.getMethod();
        String endpoint = normalizeEndpoint(uri);
        boolean isSuccess = statusCode >= 200 && statusCode < 400;

        // Calculate latency
        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        long latencyNanos = startTime != null ? System.nanoTime() - startTime : 0;
        double latencyMs = latencyNanos / 1_000_000.0;

        // Record to Micrometer (per-endpoint metrics for Prometheus/Grafana)
        Timer.builder("http_request_duration_seconds")
                .tag("endpoint", endpoint)
                .tag("method", method)
                .tag("status", String.valueOf(statusCode))
                .tag("success", String.valueOf(isSuccess))
                .register(meterRegistry)
                .record(java.time.Duration.ofNanos(latencyNanos));

        // Feed the SLO engine — this is the automatic measurement
        if (isSuccess) {
            sloService.recordSuccess(endpoint, latencyMs);
        } else {
            sloService.recordFailure(endpoint, statusCode, latencyMs);
        }

        if (latencyMs > 1000) {
            log.warn("Slow request detected: {} {} → {}ms (status: {})", method, endpoint, (long) latencyMs, statusCode);
        }
    }

    /**
     * Normalize dynamic path segments (e.g., /api/orders/123 → /api/orders/{id}).
     * This prevents metric cardinality explosion from unique IDs.
     */
    private String normalizeEndpoint(String uri) {
        return uri.replaceAll("/\\d+", "/{id}");
    }
}
