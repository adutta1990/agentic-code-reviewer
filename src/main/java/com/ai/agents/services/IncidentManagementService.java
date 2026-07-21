package com.ai.agents.services;

import com.ai.agents.agenticService.IncidentManagementAgent;
import com.ai.agents.domain.Incident;
import com.ai.agents.domain.IncidentReport;
import com.ai.agents.domain.IncidentResolution;
import com.ai.agents.tools.RemediationAudit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates a single incident analysis: run the agent, pair its verdict with the audit
 * trail, and make sure a model failure still produces a usable answer.
 */
@Service
public class IncidentManagementService {

    private static final Logger log = LoggerFactory.getLogger(IncidentManagementService.class);

    private final IncidentManagementAgent agent;
    private final RemediationAudit audit;

    public IncidentManagementService(IncidentManagementAgent agent, RemediationAudit audit) {
        this.agent = agent;
        this.audit = audit;
    }

    public IncidentReport analyze(Incident incident) {
        String incidentId = "INC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long start = System.currentTimeMillis();
        log.info("[{}] analysis started", incidentId);

        // Bracket the audit trail around this one analysis. Without start(), a batch of alerts
        // from the Alertmanager webhook would each inherit the previous alert's actions.
        audit.start();
        try {
            IncidentResolution resolution = agent.resolve(incident);
            long ms = System.currentTimeMillis() - start;
            log.info("[{}] analysis completed in {}ms", incidentId, ms);
            return new IncidentReport(incidentId, incident, resolution, audit.entries(), Instant.now(), ms);

        } catch (Exception e) {
            // An incident agent that returns a 500 during an outage is worse than useless.
            // Degrade to an explicit escalation so the on-call engineer gets a real handoff —
            // including any actions that were already applied before the failure.
            long ms = System.currentTimeMillis() - start;
            log.error("[{}] analysis failed after {}ms", incidentId, ms, e);
            return new IncidentReport(incidentId, incident, escalationFor(e), audit.entries(), Instant.now(), ms);

        } finally {
            audit.clear();
        }
    }

    private IncidentResolution escalationFor(Exception e) {
        return new IncidentResolution(
                "Automated analysis failed; this incident needs a human.",
                "Undetermined — the agent could not complete its analysis.",
                IncidentResolution.Confidence.LOW,
                // Assume the worst on failure: an unanalyzed incident that someone bothered to
                // report is more likely serious than cosmetic, and under-triaging is the
                // costlier error here.
                IncidentResolution.Severity.SEV2,
                List.of("Agent error: " + e.getClass().getSimpleName() + ": " + e.getMessage()),
                List.of(new IncidentResolution.ResolutionStep(
                        "Triage manually. Check the application logs for the agent failure above; "
                                + "if it is a model or vector-store connectivity problem, the incident itself is "
                                + "still unexamined and no conclusions should be drawn from this report.",
                        null, false, "None — nothing was changed.")),
                List.of(),
                true,
                "The agent threw " + e.getClass().getSimpleName() + " before reaching a conclusion.");
    }
}
