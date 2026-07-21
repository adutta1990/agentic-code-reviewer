package com.ai.agents.controllers;

import com.ai.agents.domain.Incident;
import com.ai.agents.domain.IncidentReport;
import com.ai.agents.services.IncidentManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/incidents")
public class IncidentManagementController {

    private final IncidentManagementService service;

    public IncidentManagementController(IncidentManagementService service) {
        this.service = service;
    }

    /**
     * Analyze an incident.
     *
     * <p>Synchronous and slow by design — the agent makes several tool calls and model
     * round-trips before answering, so expect seconds, not milliseconds. If you wire this to
     * a real alerting webhook, hand off to a queue rather than blocking the caller.
     *
     * <pre>{@code
     * curl -X POST localhost:8080/api/incidents/analyze \
     *   -H 'Content-Type: application/json' \
     *   -d '{"alertName":"KubePodCrashLooping","service":"checkout-api","namespace":"production",
     *        "description":"Pods restarting repeatedly since 14:10"}'
     * }</pre>
     */
    @PostMapping("/analyze")
    public ResponseEntity<IncidentReport> analyze(@RequestBody Incident incident) {
        return ResponseEntity.ok(service.analyze(incident));
    }

    /**
     * Alertmanager webhook adapter: unwraps a webhook payload into one analysis per firing alert.
     *
     * <p>Resolved alerts are ignored — there is nothing to fix.
     */
    @PostMapping("/webhook/alertmanager")
    public ResponseEntity<List<IncidentReport>> alertmanager(@RequestBody Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> alerts =
                (List<Map<String, Object>>) payload.getOrDefault("alerts", List.of());

        List<IncidentReport> reports = alerts.stream()
                .filter(a -> "firing".equals(a.get("status")))
                .map(this::toIncident)
                .map(service::analyze)
                .toList();

        return ResponseEntity.ok(reports);
    }

    private Incident toIncident(Map<String, Object> alert) {
        @SuppressWarnings("unchecked")
        Map<String, String> labels = (Map<String, String>) alert.getOrDefault("labels", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, String> annotations = (Map<String, String>) alert.getOrDefault("annotations", Map.of());

        return new Incident(
                labels.get("alertname"),
                // Alertmanager labels vary by setup; try the usual suspects in order of specificity.
                firstNonBlank(labels.get("deployment"), labels.get("service"), labels.get("job"), labels.get("pod")),
                labels.getOrDefault("namespace", "default"),
                firstNonBlank(annotations.get("description"), annotations.get("summary")),
                labels,
                Instant.now());
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
