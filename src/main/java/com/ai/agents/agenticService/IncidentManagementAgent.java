package com.ai.agents.agenticService;

import com.ai.agents.config.IncidentAgentProperties;
import com.ai.agents.domain.Incident;
import com.ai.agents.domain.IncidentResolution;
import com.ai.agents.tools.DiagnosticTools;
import com.ai.agents.tools.RemediationTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * The incident-resolution agent.
 *
 * <p>Three things are wired together here:
 * <ol>
 *   <li><b>RAG</b> — {@link QuestionAnswerAdvisor} retrieves the most relevant runbooks from
 *       pgvector and injects them into the prompt before the model sees the incident.</li>
 *   <li><b>Tools</b> — diagnostics (free) and remediation (gated). Spring AI 2.0 auto-registers
 *       the {@code ToolCallingAdvisor}, so the request → tool-call → result → re-request loop
 *       runs on its own; we do not hand-write it.</li>
 *   <li><b>Structured output</b> — {@code .entity()} binds the answer into
 *       {@link IncidentResolution} rather than leaving prose to be parsed.</li>
 * </ol>
 */
@Service
public class IncidentManagementAgent {

    private static final Logger log = LoggerFactory.getLogger(IncidentManagementAgent.class);

    private final ChatClient chatClient;

    public IncidentManagementAgent(
            ChatClient.Builder builder,
            VectorStore vectorStore,
            DiagnosticTools diagnosticTools,
            RemediationTools remediationTools,
            IncidentAgentProperties props) {

        var searchRequest = SearchRequest.builder()
                .topK(props.getRetrieval().getTopK())
                .similarityThreshold(props.getRetrieval().getSimilarityThreshold())
                .build();

        this.chatClient = builder
                .defaultSystem(systemPrompt(props))
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(searchRequest)
                        .build())
                .defaultTools(diagnosticTools, remediationTools)
                .build();
    }

    /**
     * Diagnose and, where policy allows, resolve the incident.
     *
     * <p>Blocking and potentially slow: the agent makes several tool calls before answering,
     * so a single invocation is multiple model round-trips.
     */
    public IncidentResolution resolve(Incident incident) {
        log.info("Resolving incident: alert={} service={} namespace={}",
                incident.alertName(), incident.service(), incident.namespace());

        IncidentResolution resolution = chatClient.prompt()
                .user(incident.toQuery())
                .call()
                .entity(IncidentResolution.class);

        log.info("Resolved: confidence={} severity={} escalate={}",
                resolution.confidence(), resolution.severity(), resolution.requiresHumanEscalation());
        return resolution;
    }

    /**
     * The system prompt.
     *
     * <p>Written against two specific failure modes seen in incident agents:
     * <ul>
     *   <li><b>Concluding before looking.</b> A model handed a runbook will happily recite its
     *       Mitigation section without checking whether the runbook's cause is <em>this</em>
     *       incident's cause. Hence the explicit "diagnose before you conclude" ordering.</li>
     *   <li><b>Fabricated evidence.</b> Asked for findings, a model will invent plausible log
     *       lines. Hence the rule that every finding must come from an actual tool result.</li>
     * </ul>
     *
     * <p>The remediation policy is stated here so the model produces sensible recommendations
     * and doesn't waste turns on tools that will refuse. It is <em>not</em> what enforces the
     * policy — {@code RemediationTools} does that in Java.
     */
    private static String systemPrompt(IncidentAgentProperties props) {
        var rem = props.getRemediation();
        String policy = rem.isAutoRemediate()
                ? """
                  Auto-remediation is ENABLED, but only for these actions: %s.
                  You may apply those yourself. Any other mutating action must be reported as a
                  recommended step for a human, never attempted.""".formatted(rem.getAllowedActions())
                : """
                  Auto-remediation is DISABLED. You have remediation tools, but every one of them
                  will refuse. Do not call them. Diagnose fully using the read-only tools and report
                  the fix as recommended steps with exact commands.""";

        return """
               You are an on-call site reliability engineer resolving a production incident. You are
               technical, direct, and you do not speculate when you can check.

               ## Method

               Work in this order. Do not skip ahead.

               1. READ THE RUNBOOKS. Relevant runbooks are supplied below the incident. Treat their
                  Diagnosis sections as a checklist of what to verify — not as a conclusion. A runbook
                  describes a class of failure; you must confirm it matches THIS incident.
               2. GATHER EVIDENCE. Call the read-only diagnostic tools before forming a conclusion.
                  Start with getWorkloadStatus. Follow the evidence: exit code 137 / OOMKilled means
                  check memory; a sudden onset means check deployment history; an application error
                  means read the logs. Call as many as you need — they are free and safe.
               3. FORM A ROOT CAUSE. State the single most likely cause, grounded in what the tools
                  actually returned.
               4. RESOLVE OR RECOMMEND, per the policy below.

               ## Evidence discipline

               Every entry in diagnosticFindings MUST come from an actual tool result in this session.
               Quote the real values you saw — restart counts, exit codes, metric percentages, log
               lines. If you did not call a tool, you have no finding. Never invent a log line, a
               metric, or a version number. If the tools genuinely do not explain the incident, say
               so, set confidence to LOW, and escalate: an honest "I could not determine this" is a
               correct and useful answer. A confident wrong diagnosis at 3am is actively harmful.

               ## Fix the cause, not the symptom

               A restart that clears a symptom is not a resolution if the cause remains — an
               under-provisioned memory limit will OOM again in minutes, a bad config will crash on
               the next start. When a restart only buys time, say that explicitly and give the real
               fix as the primary step.

               ## Remediation policy

               %s

               Regardless of policy: set requiresHumanEscalation to true when the root cause is
               unclear, when the fix risks data loss, or when the blast radius is larger than a
               single workload.

               ## Output

               Populate every field of the schema. resolutionSteps must be ordered with the most
               important first, each with the exact command where one applies. Set applied=true only
               for steps you actually executed successfully via a tool — a step you merely recommend
               is applied=false. List in runbooksConsulted only the runbooks you genuinely used.
               """.formatted(policy);
    }
}
