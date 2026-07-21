package com.ai.agents.config;

import com.ai.agents.tools.RemediationAction;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumSet;
import java.util.Set;

/** Binds the {@code incident-agent.*} block in application.yaml. */
@ConfigurationProperties(prefix = "incident-agent")
public class IncidentAgentProperties {

    private Ingestion ingestion = new Ingestion();
    private Retrieval retrieval = new Retrieval();
    private Remediation remediation = new Remediation();

    public Ingestion getIngestion() {
        return ingestion;
    }

    public void setIngestion(Ingestion ingestion) {
        this.ingestion = ingestion;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public void setRetrieval(Retrieval retrieval) {
        this.retrieval = retrieval;
    }

    public Remediation getRemediation() {
        return remediation;
    }

    public void setRemediation(Remediation remediation) {
        this.remediation = remediation;
    }

    public static class Ingestion {
        /** Ingest the runbook corpus into pgvector on startup. */
        private boolean enabled = true;
        /** Re-ingest even if the vector table already has rows (costs embedding calls). */
        private boolean force = false;
        private String corpusLocation = "classpath:rag-corpus/runbooks/*.md";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isForce() {
            return force;
        }

        public void setForce(boolean force) {
            this.force = force;
        }

        public String getCorpusLocation() {
            return corpusLocation;
        }

        public void setCorpusLocation(String corpusLocation) {
            this.corpusLocation = corpusLocation;
        }
    }

    public static class Retrieval {
        /** How many runbooks to inject into the prompt. */
        private int topK = 4;
        /** Similarity floor; below this a runbook is considered irrelevant and dropped. */
        private double similarityThreshold = 0.35;

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public void setSimilarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }
    }

    public static class Remediation {
        /**
         * Master switch. False (the default) means the agent diagnoses and recommends but
         * never mutates anything. Enable only once you trust it against your own systems.
         */
        private boolean autoRemediate = false;

        /**
         * Actions permitted when {@link #autoRemediate} is true. Anything absent from this
         * set is escalated to a human even in auto mode — this is how you allow a restart
         * but still require sign-off for a rollback.
         */
        private Set<RemediationAction> allowedActions = EnumSet.noneOf(RemediationAction.class);

        public boolean isAutoRemediate() {
            return autoRemediate;
        }

        public void setAutoRemediate(boolean autoRemediate) {
            this.autoRemediate = autoRemediate;
        }

        public Set<RemediationAction> getAllowedActions() {
            return allowedActions;
        }

        public void setAllowedActions(Set<RemediationAction> allowedActions) {
            // EnumSet.copyOf throws on an empty non-EnumSet collection, which an empty
            // allowed-actions list in YAML would produce.
            this.allowedActions = (allowedActions == null || allowedActions.isEmpty())
                    ? EnumSet.noneOf(RemediationAction.class)
                    : EnumSet.copyOf(allowedActions);
        }

        /** A mutating action is permitted only if auto-remediation is on AND it is allowlisted. */
        public boolean permits(RemediationAction action) {
            return autoRemediate && allowedActions.contains(action);
        }
    }
}
