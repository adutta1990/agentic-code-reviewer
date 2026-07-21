package com.ai.agents.domain;

import java.time.Instant;
import java.util.Map;

/**
 * An incident handed to the agent for resolution.
 *
 * <p>Modelled on an Alertmanager webhook payload, since that is the most common way an
 * incident actually arrives. {@code alertName} is the highest-signal field for retrieval:
 * it usually matches a runbook filename in the corpus (e.g. {@code KubePodCrashLooping}).
 *
 * @param alertName   firing alert, e.g. "KubePodCrashLooping". May be null for free-form reports.
 * @param service     affected service/workload, e.g. "checkout-api".
 * @param namespace   Kubernetes namespace, e.g. "production".
 * @param description operator's description of the symptoms. Used when alertName is absent.
 * @param labels      arbitrary alert labels (severity, cluster, pod, ...).
 * @param firedAt     when the alert started firing.
 */
public record Incident(
        String alertName,
        String service,
        String namespace,
        String description,
        Map<String, String> labels,
        Instant firedAt) {

    public Incident {
        labels = labels == null ? Map.of() : Map.copyOf(labels);
        firedAt = firedAt == null ? Instant.now() : firedAt;
    }

    /**
     * The natural-language query used for both RAG retrieval and the model prompt.
     * Concatenating the alert name with the symptoms retrieves better than either alone:
     * the alert name anchors to the right runbook, the description disambiguates the cause.
     */
    public String toQuery() {
        var sb = new StringBuilder();
        if (alertName != null && !alertName.isBlank()) {
            sb.append("Alert: ").append(alertName).append('\n');
        }
        if (service != null && !service.isBlank()) {
            sb.append("Service: ").append(service).append('\n');
        }
        if (namespace != null && !namespace.isBlank()) {
            sb.append("Namespace: ").append(namespace).append('\n');
        }
        if (description != null && !description.isBlank()) {
            sb.append("Symptoms: ").append(description).append('\n');
        }
        if (!labels.isEmpty()) {
            sb.append("Labels: ").append(labels).append('\n');
        }
        return sb.toString().strip();
    }
}
