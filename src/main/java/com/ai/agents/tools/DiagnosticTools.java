package com.ai.agents.tools;

import com.ai.agents.infra.InfrastructureClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Read-only tools. The agent may call these as often as it likes — none of them change
 * anything, so there is no approval gate. They exist as separate tools rather than one
 * generic "run kubectl" tool so each call is typed, loggable, and individually auditable.
 *
 * <p>Tool descriptions are prompt surface: the model decides what to call based purely on
 * these strings. They state <em>when</em> to call, not just what the method does.
 */
@Component
public class DiagnosticTools {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticTools.class);

    private final InfrastructureClient infra;

    public DiagnosticTools(InfrastructureClient infra) {
        this.infra = infra;
    }

    @Tool(description = """
            Get the current state of a workload: replica counts, restart count, last exit code,
            termination reason, per-pod phase, and running image tag. Call this FIRST for almost
            any incident — exit code 137 with reason OOMKilled, or a high restart count, usually
            identifies the root cause on its own.""")
    public InfrastructureClient.WorkloadStatus getWorkloadStatus(
            @ToolParam(description = "Kubernetes namespace, e.g. 'production'") String namespace,
            @ToolParam(description = "Workload/deployment name, e.g. 'checkout-api'") String workload) {
        log.info("TOOL getWorkloadStatus({}, {})", namespace, workload);
        return infra.getWorkloadStatus(namespace, workload);
    }

    @Tool(description = """
            Fetch the most recent application log lines for a workload, oldest first. Call this
            when you need the actual error text or stack trace behind a failure — status alone
            tells you a container died, logs tell you why.""")
    public List<String> getRecentLogs(
            @ToolParam(description = "Kubernetes namespace, e.g. 'production'") String namespace,
            @ToolParam(description = "Workload/deployment name, e.g. 'checkout-api'") String workload,
            @ToolParam(description = "How many recent lines to return. Use 50 unless you need more.") int lines) {
        log.info("TOOL getRecentLogs({}, {}, {})", namespace, workload, lines);
        return infra.getRecentLogs(namespace, workload, lines > 0 ? lines : 50);
    }

    @Tool(description = """
            Read a single metric's current value and recent trend for a workload. Supported metric
            names: 'memory.usage.percent', 'cpu.usage.percent', 'disk.usage.percent',
            'error.rate.percent'. Call this to confirm a hypothesis quantitatively — e.g. check
            memory before concluding an OOM, or disk before concluding a volume is full.""")
    public InfrastructureClient.MetricSample getMetric(
            @ToolParam(description = "Kubernetes namespace, e.g. 'production'") String namespace,
            @ToolParam(description = "Workload/deployment name, e.g. 'checkout-api'") String workload,
            @ToolParam(description = "Metric name, e.g. 'memory.usage.percent'") String metricName) {
        log.info("TOOL getMetric({}, {}, {})", namespace, workload, metricName);
        return infra.getMetric(namespace, workload, metricName);
    }

    @Tool(description = """
            List recent deployments for a workload, newest first, including image tag and time.
            Call this whenever a problem started suddenly — if the failure began right after a
            rollout, the new revision is the prime suspect and a rollback is likely the fix.""")
    public List<InfrastructureClient.DeploymentEvent> getDeploymentHistory(
            @ToolParam(description = "Kubernetes namespace, e.g. 'production'") String namespace,
            @ToolParam(description = "Workload/deployment name, e.g. 'payments-api'") String workload) {
        log.info("TOOL getDeploymentHistory({}, {})", namespace, workload);
        return infra.getDeploymentHistory(namespace, workload);
    }
}
