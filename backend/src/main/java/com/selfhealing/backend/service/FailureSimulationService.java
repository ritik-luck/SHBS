package com.selfhealing.backend.service;

import org.springframework.stereotype.Service;

@Service
public class FailureSimulationService {

    public void simulate(String type) {

        switch (type.toLowerCase()) {

            case "timeout":
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Timeout simulation interrupted");
                }
                throw new RuntimeException("Simulated Timeout");

            case "exception":
                throw new RuntimeException("Simulated Exception");

            case "database":
                throw new RuntimeException("Simulated Database Failure");

            case "network":
                throw new RuntimeException("Simulated Network Failure");

            default:
                throw new RuntimeException("Unknown failure type");
        }
    }
}