package com.ai.agents.tools;

/**
 * The mutating actions the agent is capable of.
 *
 * <p>Each constant is an allowlist entry in {@code incident-agent.remediation.allowed-actions},
 * which is how an operator grants the agent the ability to restart a workload while still
 * withholding the ability to roll back a deployment.
 */
public enum RemediationAction {
    RESTART_WORKLOAD,
    SCALE_WORKLOAD,
    ROLLBACK_DEPLOYMENT
}
