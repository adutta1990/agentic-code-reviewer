package com.ai.agents.tools;

import com.ai.agents.config.IncidentAgentProperties;
import com.ai.agents.infra.InfrastructureClient;
import com.ai.agents.infra.SimulatedInfrastructureClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the remediation gate — the one component where a bug means the agent mutates
 * production without permission.
 *
 * <p>These run with no database, no API key, and no model: the gate is deliberately plain
 * Java precisely so it is provable in isolation. That is the argument for enforcing policy
 * in code rather than in the system prompt — you cannot write this test against a prompt.
 */
class RemediationGateTest {

    private record Fixture(RemediationTools tools, RemediationAudit audit, InfrastructureClient infra) {
    }

    private Fixture fixture(boolean autoRemediate, Set<RemediationAction> allowed) {
        var props = new IncidentAgentProperties();
        props.getRemediation().setAutoRemediate(autoRemediate);
        props.getRemediation().setAllowedActions(allowed);
        var audit = new RemediationAudit();
        var infra = new SimulatedInfrastructureClient();
        return new Fixture(new RemediationTools(infra, props, audit), audit, infra);
    }

    @Test
    @DisplayName("auto-remediate=false refuses every mutating action and changes nothing")
    void refusesWhenDisabled() {
        var f = fixture(false, EnumSet.allOf(RemediationAction.class));

        assertThat(f.tools().restartWorkload("production", "checkout-api")).startsWith("REFUSED:");
        assertThat(f.tools().scaleWorkload("production", "checkout-api", 10)).startsWith("REFUSED:");
        assertThat(f.tools().rollbackDeployment("production", "payments-api")).startsWith("REFUSED:");

        // The allowlist must not be able to override the master switch.
        assertThat(f.audit().entries())
                .hasSize(3)
                .allSatisfy(e -> assertThat(e.outcome()).isEqualTo(RemediationAudit.Outcome.REFUSED));

        // Nothing reached the infrastructure: checkout-api still shows its untouched crash loop.
        assertThat(f.infra().getWorkloadStatus("production", "checkout-api").restartCount()).isEqualTo(47);
    }

    @Test
    @DisplayName("auto-remediate=true still refuses actions outside the allowlist")
    void enforcesAllowlist() {
        var f = fixture(true, EnumSet.of(RemediationAction.RESTART_WORKLOAD));

        assertThat(f.tools().restartWorkload("production", "checkout-api")).startsWith("APPLIED:");
        // Not allowlisted, even though auto-remediation is on.
        assertThat(f.tools().rollbackDeployment("production", "payments-api")).startsWith("REFUSED:");
        assertThat(f.tools().scaleWorkload("production", "checkout-api", 5)).startsWith("REFUSED:");

        List<RemediationAudit.Entry> entries = f.audit().entries();
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).outcome()).isEqualTo(RemediationAudit.Outcome.APPLIED);
        assertThat(entries.get(1).outcome()).isEqualTo(RemediationAudit.Outcome.REFUSED);
        assertThat(entries.get(2).outcome()).isEqualTo(RemediationAudit.Outcome.REFUSED);
    }

    @Test
    @DisplayName("an allowlisted action actually reaches the infrastructure")
    void appliesWhenPermitted() {
        var f = fixture(true, EnumSet.of(RemediationAction.ROLLBACK_DEPLOYMENT));

        assertThat(f.infra().getWorkloadStatus("production", "payments-api").imageTag()).isEqualTo("v5.3.0");
        assertThat(f.tools().rollbackDeployment("production", "payments-api")).startsWith("APPLIED:");
        // The rollback took effect, not just reported success.
        assertThat(f.infra().getWorkloadStatus("production", "payments-api").imageTag()).isEqualTo("v5.2.0");
    }

    @Test
    @DisplayName("empty allowlist refuses everything even in auto mode")
    void emptyAllowlistRefusesAll() {
        var f = fixture(true, Set.of());

        assertThat(f.tools().restartWorkload("production", "checkout-api")).startsWith("REFUSED:");
        assertThat(f.audit().entries()).hasSize(1);
    }

    @Test
    @DisplayName("a refusal tells the model to recommend instead, so the run stays productive")
    void refusalGuidesTheModel() {
        var f = fixture(false, Set.of());
        String msg = f.tools().restartWorkload("production", "checkout-api");

        assertThat(msg)
                .contains("Nothing was changed")
                .contains("recommended step")
                .contains("auto-remediate=false");
    }

    @Test
    @DisplayName("start() isolates each analysis, so a batch of alerts does not share a trail")
    void startIsolatesEachAnalysis() {
        var f = fixture(false, Set.of());

        // Analysis 1
        f.audit().start();
        f.tools().restartWorkload("production", "checkout-api");
        assertThat(f.audit().entries()).hasSize(1);

        // Analysis 2 in the same thread — as happens for the second alert in an
        // Alertmanager webhook batch. It must not inherit analysis 1's action.
        f.audit().start();
        assertThat(f.audit().entries()).isEmpty();
        f.tools().rollbackDeployment("production", "payments-api");
        assertThat(f.audit().entries())
                .singleElement()
                .satisfies(e -> assertThat(e.action()).isEqualTo(RemediationAction.ROLLBACK_DEPLOYMENT));

        f.audit().clear();
    }

    @Test
    @DisplayName("an infrastructure failure is returned to the model, not thrown")
    void infrastructureFailureIsReportedNotThrown() {
        var f = fixture(true, EnumSet.of(RemediationAction.SCALE_WORKLOAD));

        // The simulator rejects out-of-range replica counts.
        String msg = f.tools().scaleWorkload("production", "checkout-api", 9999);

        assertThat(msg).startsWith("FAILED:");
        assertThat(f.audit().entries())
                .singleElement()
                .satisfies(e -> assertThat(e.outcome()).isEqualTo(RemediationAudit.Outcome.FAILED));
    }
}
