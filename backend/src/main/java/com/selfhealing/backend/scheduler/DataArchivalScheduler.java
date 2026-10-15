package com.selfhealing.backend.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * DataArchivalScheduler — Timestamp-based retention for metrics, alerts, and audit logs.
 *
 * Runs daily at 3:00 AM. Deletes rows older than the configured retention period
 * (default: 30 days) from high-volume operational tables.
 */
@Component
public class DataArchivalScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataArchivalScheduler.class);

    private final JdbcTemplate jdbc;

    @Value("${shbs.retention.audit.days:365}")
    private int auditRetentionDays;

    @Value("${shbs.retention.metrics.days:30}")
    private int metricsRetentionDays;

    private static final int BATCH_SIZE = 1000;

    public DataArchivalScheduler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 3 * * *") // Daily at 3:00 AM
    @SchedulerLock(name = "DataArchivalScheduler_archiveOldData", lockAtLeastFor = "5m", lockAtMostFor = "15m")
    public void archiveOldData() {
        LocalDateTime metricsCutoff = LocalDateTime.now().minusDays(metricsRetentionDays);
        LocalDateTime auditCutoff = LocalDateTime.now().minusDays(auditRetentionDays);

        log.info("Data archival started — Metrics Cutoff: {} ({}d), Audit Cutoff: {} ({}d)", 
                 metricsCutoff, metricsRetentionDays, auditCutoff, auditRetentionDays);

        int auditArchived = archiveAndDelete("audit_log", auditCutoff);
        int failureArchived = archiveAndDelete("failure_metric", metricsCutoff);
        int recoveryArchived = archiveAndDelete("recovery_metric", metricsCutoff);
        int alertArchived = archiveAndDelete("alert", metricsCutoff);
        int healthArchived = archiveAndDelete("health_metric", metricsCutoff);

        log.info("Data archival complete — archived & deleted: failures={}, audit={}, alerts={}, recovery={}, health={}",
                failureArchived, auditArchived, alertArchived, recoveryArchived, healthArchived);
    }

    private int archiveAndDelete(String tableName, LocalDateTime cutoff) {
        int totalProcessed = 0;
        boolean hasMore = true;

        // Ensure archive directory exists
        File archiveDir = new File("archives/" + tableName);
        if (!archiveDir.exists()) {
            archiveDir.mkdirs();
        }

        while (hasMore) {
            // Select batch of old records
            String selectSql = "SELECT * FROM " + tableName + " WHERE timestamp < ? LIMIT ?";
            List<Map<String, Object>> batch = jdbc.queryForList(selectSql, cutoff, BATCH_SIZE);

            if (batch.isEmpty()) {
                hasMore = false;
                continue;
            }

            // Mock archiving to local disk (simulating S3)
            archiveToDisk(tableName, batch);

            // Delete the exact batch by timestamp cutoff (using LIMIT is MySQL specific, standard is ID based, but this is simple for the demo)
            // To avoid replication lag issues and long locks, delete by LIMIT
            String deleteSql = "DELETE FROM " + tableName + " WHERE timestamp < ? LIMIT ?";
            int deleted = jdbc.update(deleteSql, cutoff, BATCH_SIZE);

            totalProcessed += deleted;
            if (deleted == 0) {
                hasMore = false;
            }
        }
        return totalProcessed;
    }

    private void archiveToDisk(String tableName, List<Map<String, Object>> batch) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File archiveFile = new File("archives/" + tableName + "/archive_" + timestamp + ".json");
        
        try (FileWriter writer = new FileWriter(archiveFile, true)) {
            // Write basic JSON array mock
            writer.write("[");
            for (int i = 0; i < batch.size(); i++) {
                writer.write(batch.get(i).toString());
                if (i < batch.size() - 1) writer.write(",");
            }
            writer.write("]\n");
        } catch (IOException e) {
            log.error("Failed to archive {} records to disk", tableName, e);
        }
    }
}
