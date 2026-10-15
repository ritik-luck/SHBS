package com.selfhealing.backend.scheduler;

import com.selfhealing.backend.service.ChaosAgentService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ChaosAgentScheduler — Triggers the AI Chaos Agent on a configurable cron schedule.
 *
 * Defaults to every 6 hours. Uses ShedLock to ensure only one replica
 * runs the experiment at a time.
 *
 * Enable/disable via: chaos.agent.enabled=true/false
 * Change schedule via: chaos.agent.cron=0 0 *\/6 * * *
 */
@Component
public class ChaosAgentScheduler {

    private static final Logger log = LoggerFactory.getLogger(ChaosAgentScheduler.class);

    private final ChaosAgentService chaosAgentService;

    @Value("${chaos.agent.enabled:false}")
    private boolean enabled;

    public ChaosAgentScheduler(ChaosAgentService chaosAgentService) {
        this.chaosAgentService = chaosAgentService;
    }

    /**
     * Runs the chaos experiment on a cron schedule.
     * Default: every 6 hours (0 0 0/6 * * *).
     * ShedLock ensures only one instance runs across replicas.
     */
    @Scheduled(cron = "${chaos.agent.cron:0 0 */6 * * *}")
    @SchedulerLock(name = "chaos-agent-experiment", lockAtMostFor = "5m", lockAtLeastFor = "2m")
    public void runScheduledExperiment() {
        if (!enabled) {
            log.debug("Chaos Agent is disabled (chaos.agent.enabled=false). Skipping.");
            return;
        }

        log.info("🤖 Chaos Agent Scheduler: Triggering autonomous experiment...");
        try {
            chaosAgentService.runExperiment();
        } catch (Exception e) {
            log.error("Chaos Agent Scheduler: Experiment failed unexpectedly", e);
        }
    }
}
