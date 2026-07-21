package com.ai.agents.domain;

import com.ai.agents.tools.RemediationAudit;

import java.time.Instant;
import java.util.List;

/**
 * What the API returns: the agent's resolution plus the independently-recorded trail of
 * what it actually did.
 *
 * <p>The two are kept separate on purpose. {@code resolution} is the model's claim;
 * {@code actionsTaken} is the system's record. If the model says it restarted something and
 * the audit trail disagrees, you can see that — which you could not if the agent's own
 * narration were the only account of its behaviour.
 *
 * @param incidentId   correlation id for this analysis.
 * @param incident     the incident as submitted.
 * @param resolution   the agent's verdict.
 * @param actionsTaken every mutating action attempted, including refusals.
 * @param analyzedAt   when the analysis completed.
 * @param durationMs   wall-clock time, including all model round-trips and tool calls.
 */
public record IncidentReport(
        String incidentId,
        Incident incident,
        IncidentResolution resolution,
        List<RemediationAudit.Entry> actionsTaken,
        Instant analyzedAt,
        long durationMs) {

    public IncidentReport {
        actionsTaken = actionsTaken == null ? List.of() : List.copyOf(actionsTaken);
    }
}
