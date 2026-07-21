package com.ai.agents.agenticService;

import com.ai.agents.domain.PullRequest;
import com.ai.agents.domain.TriageVerdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The static-analysis triage engine.
 *
 * <p>A single, tool-free model call: it reads the PR's diffs and decides, per file, whether
 * the change warrants deep review. No RAG, no remediation — the model judges only the hunks
 * it is shown. {@code .entity()} binds the answer straight into a list of
 * {@link TriageVerdict}, so the model returns structured decisions rather than prose we would
 * have to parse.
 */
@Service
public class TriageAgent {

    private static final Logger log = LoggerFactory.getLogger(TriageAgent.class);

    private static final ParameterizedTypeReference<List<TriageVerdict>> VERDICT_LIST =
            new ParameterizedTypeReference<>() {};

    private final ChatClient chatClient;

    public TriageAgent(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    /**
     * Triage every changed file in the pull request.
     *
     * <p>One model round-trip. Returns one verdict per changed file; the list preserves no
     * guaranteed ordering, so callers key on {@link TriageVerdict#path()}.
     */
    public List<TriageVerdict> triage(PullRequest pr) {
        log.info("Triaging PR: repo={} base={} files={}",
                pr.repo(), pr.baseRef(), pr.changedFiles().size());

        List<TriageVerdict> verdicts = chatClient.prompt()
                .user(pr.toPrompt())
                .call()
                .entity(VERDICT_LIST);

        long worth = verdicts == null ? 0 : verdicts.stream().filter(TriageVerdict::worthReview).count();
        log.info("Triage complete: {} of {} files flagged worth_review",
                worth, verdicts == null ? 0 : verdicts.size());
        return verdicts == null ? List.of() : verdicts;
    }

    /**
     * The triage system prompt.
     *
     * <p>Two failure modes it is written against: a model that reviews files it cannot actually
     * see (inferring contents beyond the hunks), and one that under-triages. The rules make the
     * conservative direction explicit — when uncertain, flag it — because a missed defect costs
     * far more than a wasted review.
     */
    private static final String SYSTEM_PROMPT = """
            You are a static-analysis triage engine for Java pull requests. You do not
            write code. You decide whether a changed file warrants deep review.

            Rules:
            - Judge ONLY the hunks provided. Never infer file contents you cannot see.
            - Generated code, lock files, and pure import reordering are NEVER worth review.
            - Test files are worth review only if they contain assertions that are
              tautological or absent.
            - If you are uncertain, mark worth_review=true. False positives are cheap here;
              false negatives are not.

            Return one entry per changed file. For each: copy the path exactly, decide
            worth_review, assign a risk of LOW, MEDIUM, or HIGH, give a reason of at most 20
            words grounded only in the visible hunks, and list focus areas (CORRECTNESS,
            CONCURRENCY, RESOURCE_LEAK, SECURITY, STYLE, TESTABILITY) — empty when the file is
            not worth review.
            """;
}
