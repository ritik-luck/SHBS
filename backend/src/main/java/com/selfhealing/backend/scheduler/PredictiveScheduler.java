package com.selfhealing.backend.scheduler;

import com.selfhealing.backend.service.CircuitBreakerService;
import com.selfhealing.backend.service.PredictiveMLService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PredictiveScheduler — Gathers telemetry and runs ML predictions periodically.
 *
 * This demonstrates the proactive AIOps side of the system.
 * It simulates collecting metrics from Prometheus/Micrometer, passes them
 * to the PredictiveMLService (ONNX model), and preemptively trips the circuit
 * breaker if a crash is predicted.
 */

@Component
public class PredictiveScheduler {

    private static final Logger log = LoggerFactory.getLogger(PredictiveScheduler.class);

    private final PredictiveMLService mlService;
    private final CircuitBreakerService circuitBreakerService;

    public PredictiveScheduler(PredictiveMLService mlService, CircuitBreakerService circuitBreakerService) {
        this.mlService = mlService;
        this.circuitBreakerService = circuitBreakerService;
    }

    /**
     * Run the predictive model every 10 seconds.
     */
    @Scheduled(fixedRate = 10000)
    public void runPredictiveAnalysis() {
        log.debug("🔍 AIOps: Gathering current system telemetry...");

        // In a real system, we would query the MeterRegistry or Prometheus here.
        // For the demo, we generate realistic random metrics, but occasionally
        // spike them to trigger a proactive failure prediction.
        
        double cpu = generateTelemetry(30.0, 10.0, 95.0);
        double mem = generateTelemetry(45.0, 5.0, 90.0);
        double lat = generateTelemetry(50.0, 20.0, 2000.0);
        double err = generateTelemetry(0.01, 0.05, 0.5);

        log.debug("📊 AIOps Telemetry: CPU={}% Mem={}% Latency={}ms Err={}%",
                String.format("%.1f", cpu),
                String.format("%.1f", mem),
                String.format("%.1f", lat),
                String.format("%.3f", err * 100));

        // Use the ONNX ML model to predict if these metrics mean imminent failure
        boolean failurePredicted = mlService.predictFailure(cpu, mem, lat, err);

        if (failurePredicted) {
            log.warn("🚨 AIOps ALERT: ML Model predicts imminent system failure based on current telemetry!");
            
            // PREEMPTIVE CIRCUIT BREAKING:
            // We open the circuit BEFORE requests actually start failing, saving the downstream system
            // from being completely overwhelmed, and ensuring our users get fast fallback responses.
            circuitBreakerService.tripCircuit("inventory-service");
        }
    }

    /**
     * Generates a random telemetry metric. 5% chance of returning the spiked value.
     */
    private double generateTelemetry(double mean, double stdDev, double spikedValue) {
        if (ThreadLocalRandom.current().nextDouble() < 0.05) {
            return spikedValue;
        }
        return Math.max(0, mean + ThreadLocalRandom.current().nextGaussian() * stdDev);
    }
}
