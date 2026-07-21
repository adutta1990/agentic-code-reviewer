package com.ai.agents.services;

import com.ai.agents.agenticService.TriageAgent;
import com.ai.agents.domain.ChangedFile;
import com.ai.agents.domain.PullRequest;
import com.ai.agents.domain.TriageVerdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates a single PR triage: run the engine, and make sure a model failure still
 * produces a usable, conservative answer instead of a 500.
 */
@Service
public class TriageService {

    private static final Logger log = LoggerFactory.getLogger(TriageService.class);

    private final TriageAgent agent;

    public TriageService(TriageAgent agent) {
        this.agent = agent;
    }

    public List<TriageVerdict> triage(PullRequest pr) {
        long start = System.currentTimeMillis();
        try {
            List<TriageVerdict> verdicts = agent.triage(pr);
            log.info("Triage succeeded in {}ms", System.currentTimeMillis() - start);
            return verdicts;

        } catch (Exception e) {
            // A triage engine that 500s just drops files silently from review. Fail safe
            // instead: flag every changed file for review so nothing slips through unexamined.
            log.error("Triage failed after {}ms; failing safe to worth_review=true for all files",
                    System.currentTimeMillis() - start, e);
            return failSafe(pr, e);
        }
    }

    private List<TriageVerdict> failSafe(PullRequest pr, Exception e) {
        String reason = "Triage engine failed (" + e.getClass().getSimpleName() + "); flagged for manual review.";
        return pr.changedFiles().stream()
                .map(ChangedFile::path)
                .map(path -> new TriageVerdict(
                        path, true, TriageVerdict.Risk.MEDIUM, reason, List.of(TriageVerdict.FocusArea.CORRECTNESS)))
                .toList();
    }
}
