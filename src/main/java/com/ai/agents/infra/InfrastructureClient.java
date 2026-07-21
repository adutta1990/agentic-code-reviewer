package com.ai.agents.infra;

import java.util.List;

/**
 * The agent's window onto live infrastructure.
 *
 * <p>Deliberately split into read operations (safe, the agent calls these freely) and
 * mutating operations (gated — see {@code RemediationTools}). Everything the agent can
 * actually <em>do</em> to your systems passes through this interface, which makes it the
 * one place to audit blast radius.
 *
 * <p>{@link SimulatedInfrastructureClient} is the default so the agent is runnable without
 * a cluster. To point it at real infrastructure, implement this against the Kubernetes
 * Java client / your metrics backend and register it as a {@code @Primary} bean.
 */
public interface InfrastructureClient {

    // ---------- Read operations (safe) ----------

    /** Current state of a workload: replicas, restarts, last exit code, pod phases. */
    WorkloadStatus getWorkloadStatus(String namespace, String workload);

    /** Most recent log lines, newest last. */
    List<String> getRecentLogs(String namespace, String workload, int lines);

    /** A single named metric's recent value, e.g. "memory.usage.percent". */
    MetricSample getMetric(String namespace, String workload, String metricName);

    /** Deployment/rollout history, newest first. */
    List<DeploymentEvent> getDeploymentHistory(String namespace, String workload);

    // ---------- Mutating operations (gated) ----------

    /** Restart all pods of a workload (rolling). */
    ActionResult restartWorkload(String namespace, String workload);

    /** Change replica count. */
    ActionResult scaleWorkload(String namespace, String workload, int replicas);

    /** Roll back to the previous known-good revision. */
    ActionResult rollbackDeployment(String namespace, String workload);

    // ---------- Payloads ----------

    record WorkloadStatus(
            String workload,
            String namespace,
            int desiredReplicas,
            int readyReplicas,
            int restartCount,
            String lastExitCode,
            String lastTerminationReason,
            List<String> podPhases,
            String imageTag) {
    }

    record MetricSample(
            String metricName,
            double value,
            String unit,
            String trend) {
    }

    record DeploymentEvent(
            String revision,
            String imageTag,
            String deployedAt,
            String deployedBy,
            boolean current) {
    }

    record ActionResult(
            boolean success,
            String detail) {
    }
}
