package com.ai.agents.tools;

import com.ai.agents.config.IncidentAgentProperties;
import com.ai.agents.infra.InfrastructureClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Mutating tools. Every one of these passes through {@link #gate} before touching anything.
 *
 * <p><strong>The gate is enforced here, in Java — not in the system prompt.</strong> That is
 * deliberate. A prompt instruction like "do not restart without permission" is a suggestion
 * the model can be argued out of by a cleverly-worded incident description; an {@code if}
 * statement in front of the infrastructure call cannot. The prompt still tells the model
 * about the policy, but only so it produces sensible recommendations — it is not what
 * enforces the policy.
 *
 * <p>When the gate refuses, the tool returns a normal (non-exception) message telling the
 * model to surface the action as a recommendation instead. That keeps the agent productive
 * in the default read-only mode: it still produces the full fix, it just doesn't apply it.
 */
@Component
public class RemediationTools {

    private static final Logger log = LoggerFactory.getLogger(RemediationTools.class);

    private final InfrastructureClient infra;
    private final IncidentAgentProperties props;
    private final RemediationAudit audit;

    public RemediationTools(InfrastructureClient infra, IncidentAgentProperties props, RemediationAudit audit) {
        this.infra = infra;
        this.props = props;
        this.audit = audit;
    }

    @Tool(description = """
            Restart all pods of a workload (rolling restart). Use this ONLY for failures a restart
            genuinely fixes, such as a wedged process or exhausted connection pool. A restart does
            NOT fix a bad configuration, an under-provisioned memory limit, or a bad image — in
            those cases the pod will crash again within minutes, so recommend the real fix instead.
            If this tool reports that remediation is disabled, do not retry it; report the restart
            as a recommended step for a human.""")
    public String restartWorkload(
            @ToolParam(description = "Kubernetes namespace, e.g. 'production'") String namespace,
            @ToolParam(description = "Workload/deployment name, e.g. 'checkout-api'") String workload) {

        return gate(RemediationAction.RESTART_WORKLOAD, target(namespace, workload), Map.of(),
                () -> infra.restartWorkload(namespace, workload));
    }

    @Tool(description = """
            Change a workload's replica count. Use to relieve load-driven degradation, or to scale
            to 0 to stop a runaway workload. Scaling up does NOT fix a per-pod resource limit
            problem such as an OOMKill — each new pod hits the same limit. If this tool reports
            that remediation is disabled, report the scale as a recommended step instead.""")
    public String scaleWorkload(
            @ToolParam(description = "Kubernetes namespace, e.g. 'production'") String namespace,
            @ToolParam(description = "Workload/deployment name, e.g. 'checkout-api'") String workload,
            @ToolParam(description = "Desired replica count, 0-100") int replicas) {

        return gate(RemediationAction.SCALE_WORKLOAD, target(namespace, workload), Map.of("replicas", replicas),
                () -> infra.scaleWorkload(namespace, workload, replicas));
    }

    @Tool(description = """
            Roll a deployment back to its previous revision. This is the correct fix when the
            deployment history shows the failure began immediately after a rollout. It is
            disruptive and discards the current revision, so it is usually gated — expect to
            recommend it rather than apply it, and always explain which revision you would roll
            back to and why.""")
    public String rollbackDeployment(
            @ToolParam(description = "Kubernetes namespace, e.g. 'production'") String namespace,
            @ToolParam(description = "Workload/deployment name, e.g. 'payments-api'") String workload) {

        return gate(RemediationAction.ROLLBACK_DEPLOYMENT, target(namespace, workload), Map.of(),
                () -> infra.rollbackDeployment(namespace, workload));
    }

    /**
     * The single choke point for every mutation. Checks the policy, runs the action only if
     * permitted, and records the attempt either way.
     */
    private String gate(RemediationAction action, String target, Map<String, Object> params,
                        InfrastructureCall call) {

        if (!props.getRemediation().permits(action)) {
            String reason = props.getRemediation().isAutoRemediate()
                    ? ("Action %s is not in the allowed-actions allowlist.".formatted(action))
                    : "Auto-remediation is disabled (incident-agent.remediation.auto-remediate=false).";
            String msg = "REFUSED: %s Nothing was changed. Report this action as a recommended step for a human operator, with the exact command, and continue your analysis."
                    .formatted(reason);
            log.warn("TOOL {} REFUSED on {} — {}", action, target, reason);
            audit.record(RemediationAudit.Entry.refused(action, target, params, reason));
            return msg;
        }

        log.warn("TOOL {} APPLYING on {} params={}", action, target, params);
        try {
            InfrastructureClient.ActionResult result = call.execute();
            if (result.success()) {
                audit.record(RemediationAudit.Entry.applied(action, target, params, result.detail()));
                return "APPLIED: " + result.detail();
            }
            audit.record(RemediationAudit.Entry.failed(action, target, params, result.detail()));
            return "FAILED: " + result.detail();
        } catch (Exception e) {
            // Surface the failure to the model as a tool result rather than throwing: the agent
            // can then adapt (try another approach, or escalate) instead of the run dying.
            log.error("TOOL {} threw on {}", action, target, e);
            audit.record(RemediationAudit.Entry.failed(action, target, params, e.toString()));
            return "FAILED: the action threw an exception: " + e.getMessage()
                    + ". Do not retry it; escalate to a human.";
        }
    }

    private static String target(String namespace, String workload) {
        return namespace + "/" + workload;
    }

    /** The mutating call to run once the gate has approved it. */
    @FunctionalInterface
    private interface InfrastructureCall {
        InfrastructureClient.ActionResult execute();
    }
}
