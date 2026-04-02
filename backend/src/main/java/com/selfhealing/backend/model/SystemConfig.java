package com.selfhealing.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class SystemConfig {

    @Id
    private String configKey;

    private String configValue;

    private String description;

    private LocalDateTime updatedAt;

    public SystemConfig() {}

    public SystemConfig(String key, String value, String desc) {
        this.configKey = key;
        this.configValue = value;
        this.description = desc;
        this.updatedAt = LocalDateTime.now();
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}