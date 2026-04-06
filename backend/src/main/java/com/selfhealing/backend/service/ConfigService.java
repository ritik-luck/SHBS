package com.selfhealing.backend.service;

import com.selfhealing.backend.model.SystemConfig;
import com.selfhealing.backend.repository.SystemConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ConfigService {

    @Autowired
    private SystemConfigRepository repository;

    public String get(String key, String defaultValue) {
        return repository.findById(key)
                .map(SystemConfig::getConfigValue)
                .orElse(defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return Integer.parseInt(get(key, String.valueOf(defaultValue)));
    }

    public void update(String key, String value, String desc) {
        SystemConfig config = new SystemConfig(key, value, desc);
        repository.save(config);
    }

    public void set(String key, String value, String description) {
        update(key, value, description);
    }
}