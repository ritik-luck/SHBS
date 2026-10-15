package com.selfhealing.backend.model;

/**
 * RemediationPlaybook — Defines safe, controlled remediation actions.
 *
 * Each action has guardrails: cooldown periods, maximum executions per hour,
 * and an approval policy that determines whether the action runs autonomously.
 */
public class RemediationPlaybook {

    private final String actionType;
    private final int cooldownSeconds;
    private final int maxExecutionsPerHour;
    private final boolean requiresApproval;
    private final String rollbackAction;

    public RemediationPlaybook(String actionType, int cooldownSeconds, int maxExecutionsPerHour,
                                boolean requiresApproval, String rollbackAction) {
        this.actionType = actionType;
        this.cooldownSeconds = cooldownSeconds;
        this.maxExecutionsPerHour = maxExecutionsPerHour;
        this.requiresApproval = requiresApproval;
        this.rollbackAction = rollbackAction;
    }

    // ── Pre-defined playbooks ──

    public static final RemediationPlaybook EVICT_CONNECTIONS = new RemediationPlaybook(
            "EVICT_CONNECTIONS", 60, 5, false, null
    );

    public static final RemediationPlaybook FLUSH_CACHE = new RemediationPlaybook(
            "FLUSH_CACHE", 30, 10, false, null
    );

    public static final RemediationPlaybook DISABLE_FEATURE = new RemediationPlaybook(
            "DISABLE_FEATURE", 120, 3, false, "ENABLE_FEATURE"
    );

    public static final RemediationPlaybook REDUCE_TRAFFIC = new RemediationPlaybook(
            "REDUCE_TRAFFIC", 300, 2, false, "RESTORE_TRAFFIC"
    );

    public static final RemediationPlaybook RESTART_SERVICE = new RemediationPlaybook(
            "RESTART_SERVICE", 600, 1, true, null // Requires human approval
    );

    // Getters
    public String getActionType() { return actionType; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public int getMaxExecutionsPerHour() { return maxExecutionsPerHour; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public String getRollbackAction() { return rollbackAction; }
}
