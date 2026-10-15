package com.selfhealing.backend.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.util.Collections;

/**
 * PredictiveMLService — AIOps model integration using ONNX Runtime.
 *
 * Loads a trained Machine Learning model (e.g., Random Forest) from an .onnx file.
 * The model predicts if a service is about to fail based on current telemetry:
 * - CPU Usage
 * - Memory Usage
 * - Latency (ms)
 * - Error Rate
 *
 * Fallback: If the .onnx file is not found (e.g., during a demo before training),
 * it uses a mathematical heuristic to simulate the model's behavior.
 */
@Service
public class PredictiveMLService {

    private static final Logger log = LoggerFactory.getLogger(PredictiveMLService.class);

    private OrtEnvironment env;
    private OrtSession session;
    private boolean useFallbackHeuristic = false;

    public PredictiveMLService() {
        try {
            log.info("🤖 AIOps: Initializing ONNX Runtime Environment...");
            env = OrtEnvironment.getEnvironment();

            ClassPathResource modelResource = new ClassPathResource("predictive_model.onnx");
            
            if (modelResource.exists()) {
                try (InputStream is = modelResource.getInputStream()) {
                    byte[] modelBytes = is.readAllBytes();
                    session = env.createSession(modelBytes, new OrtSession.SessionOptions());
                    log.info("✅ AIOps: Successfully loaded ONNX model from resources.");
                }
            } else {
                log.warn("⚠️ AIOps: 'predictive_model.onnx' not found in resources!");
                log.warn("⚠️ AIOps: Please train the model in Colab (docs/ml/train_model.py) and place the file in src/main/resources/.");
                log.warn("⚠️ AIOps: Falling back to mathematical heuristic mode for the demo.");
                useFallbackHeuristic = true;
            }

        } catch (Exception e) {
            log.error("Failed to initialize ONNX Runtime. Falling back to heuristic.", e);
            useFallbackHeuristic = true;
        }
    }

    /**
     * Predict if a failure is imminent based on current telemetry.
     *
     * @return true if failure is predicted, false if healthy
     */
    public boolean predictFailure(double cpuUsage, double memoryUsage, double latencyMs, double errorRate) {
        if (useFallbackHeuristic) {
            return predictWithHeuristic(cpuUsage, memoryUsage, latencyMs, errorRate);
        }

        try {
            // ONNX expects a 2D float array [batch_size][features]
            float[][] inputs = new float[1][4];
            inputs[0][0] = (float) cpuUsage;
            inputs[0][1] = (float) memoryUsage;
            inputs[0][2] = (float) latencyMs;
            inputs[0][3] = (float) errorRate;

            OnnxTensor tensor = OnnxTensor.createTensor(env, inputs);

            // Our python script mapped the input to 'float_input'
            try (OrtSession.Result result = session.run(Collections.singletonMap("float_input", tensor))) {
                
                // The output is typically an array of predicted classes (0 or 1)
                // For RandomForestClassifier in skl2onnx, index 0 is the label array
                Object rawLabels = result.get(0).getValue();
                
                long predictedLabel = 0;
                if (rawLabels instanceof long[]) {
                    predictedLabel = ((long[]) rawLabels)[0];
                }

                boolean failureImminent = (predictedLabel == 1);
                
                log.debug("AIOps ONNX Prediction: CPU={}, Mem={}, Latency={}, ErrRate={} -> FailureImminent={}",
                        cpuUsage, memoryUsage, latencyMs, errorRate, failureImminent);
                
                return failureImminent;
            }

        } catch (OrtException e) {
            log.error("ONNX inference failed", e);
            return false;
        }
    }

    /**
     * A simple heuristic that acts like our Random Forest model for demo purposes
     * when the actual ONNX file hasn't been provided yet.
     */
    private boolean predictWithHeuristic(double cpuUsage, double memoryUsage, double latencyMs, double errorRate) {
        int riskScore = 0;
        
        if (cpuUsage > 80.0) riskScore += 2;
        if (memoryUsage > 85.0) riskScore += 2;
        if (latencyMs > 1000.0) riskScore += 3;
        if (errorRate > 0.1) riskScore += 4;
        
        // If risk score >= 4, predict failure
        boolean failureImminent = (riskScore >= 4);
        
        log.debug("AIOps Heuristic Prediction: RiskScore={} -> FailureImminent={}", riskScore, failureImminent);
        
        return failureImminent;
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (session != null) session.close();
            if (env != null) env.close();
        } catch (OrtException e) {
            log.error("Error closing ONNX resources", e);
        }
    }
}
