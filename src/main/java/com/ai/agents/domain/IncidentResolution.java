package com.ai.agents.domain;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * The agent's structured verdict on an incident.
 *
 * <p>Spring AI derives a JSON schema from this record and binds the model's response into
 * it via {@code .entity()}. The {@link JsonPropertyDescription} annotations below are not
 * documentation — they are serialized into that schema and become part of the prompt, so
 * they are the instructions the model actually follows when filling each field. Plain
 * javadoc and {@code //} comments do <em>not</em> reach the model; only these annotations
 * do. Reword them deliberately.
 */
@JsonClassDescription("The resolution of a production incident, produced after consulting runbooks and live diagnostics.")
public record IncidentResolution(

        @JsonPropertyDescription("One or two sentences an on-call engineer can read at 3am and immediately understand.")
        String summary,

        @JsonPropertyDescription("""
                The most likely root cause, stated as a claim rather than a hedge. Ground it in the \
                diagnostic evidence you gathered. If the evidence is genuinely insufficient to name a \
                cause, say so plainly here and set confidence to LOW.""")
        String rootCause,

        @JsonPropertyDescription("HIGH only with direct diagnostic evidence and a matching runbook. MEDIUM if alternative explanations remain. LOW if the evidence is thin or contradictory.")
        Confidence confidence,

        @JsonPropertyDescription("SEV1 total customer-facing outage; SEV2 major degradation; SEV3 partial or single-tenant; SEV4 cosmetic or a warning with no impact yet.")
        Severity severity,

        @JsonPropertyDescription("""
                Concrete observations from diagnostic tool calls that support the root cause. Cite the \
                actual values you saw, e.g. 'checkout-api restarted 47 times in 20m, last exit code 137 \
                (OOMKilled)'. Do not include generic statements or anything you did not actually observe \
                via a tool call.""")
        List<String> diagnosticFindings,

        @JsonPropertyDescription("Steps that resolve the incident, ordered most important first.")
        List<ResolutionStep> resolutionSteps,

        @JsonPropertyDescription("Alert names of runbooks you actually used, e.g. ['KubePodCrashLooping']. Leave empty if no retrieved runbook was relevant. Never list a runbook you did not use.")
        List<String> runbooksConsulted,

        @JsonPropertyDescription("""
                True when a human must decide: the root cause is unclear, the fix risks data loss or is \
                otherwise destructive, or the blast radius exceeds a single workload.""")
        boolean requiresHumanEscalation,

        @JsonPropertyDescription("Why a human is needed. Null when requiresHumanEscalation is false.")
        String escalationReason) {

    public IncidentResolution {
        diagnosticFindings = diagnosticFindings == null ? List.of() : List.copyOf(diagnosticFindings);
        resolutionSteps = resolutionSteps == null ? List.of() : List.copyOf(resolutionSteps);
        runbooksConsulted = runbooksConsulted == null ? List.of() : List.copyOf(runbooksConsulted);
    }

    @JsonClassDescription("A single remediation step.")
    public record ResolutionStep(

            @JsonPropertyDescription("What this step does and why, in plain language.")
            String description,

            @JsonPropertyDescription("The exact shell or kubectl command to run. Null if this step is not a command.")
            String command,

            @JsonPropertyDescription("True ONLY if you already executed this step with a remediation tool and it succeeded. False means it is a recommendation for a human to run.")
            boolean applied,

            @JsonPropertyDescription("What could go wrong if this step is applied, e.g. 'brief request drop while pods restart'.")
            String risk) {
    }

    public enum Confidence {
        HIGH, MEDIUM, LOW
    }

    public enum Severity {
        SEV1, SEV2, SEV3, SEV4
    }
}
