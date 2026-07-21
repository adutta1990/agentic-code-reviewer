package com.ai.agents.services;

import com.ai.agents.agenticService.FixAgent;
import com.ai.agents.domain.FixProposal;
import com.ai.agents.domain.FixRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates a single fix: run the engine, then enforce the hard constraints deterministically
 * in Java before the patch can be applied.
 *
 * <p>The engine's output is compiled without a human editing step, so trusting the model to obey
 * its own constraints is not enough. Two are re-checked here and any violation downgrades the
 * proposal to a refusal: (1) the replacement must not introduce an import outside the allowed
 * whitelist, and (2) an "applicable" proposal must actually carry a non-empty replacement. On any
 * engine failure the service refuses rather than 500s — a refusal simply means the fix is left for
 * a human, which is safe.
 */
@Service
public class FixService {

    private static final Logger log = LoggerFactory.getLogger(FixService.class);

    private final FixAgent agent;

    public FixService(FixAgent agent) {
        this.agent = agent;
    }

    public FixProposal propose(FixRequest request) {
        try {
            FixProposal proposal = agent.propose(request);
            if (!proposal.applicable()) {
                return proposal;
            }

            List<String> disallowed = proposal.newImports().stream()
                    .filter(imp -> !request.allowedImports().contains(imp))
                    .toList();
            if (!disallowed.isEmpty()) {
                log.warn("Rejecting fix for {}: proposed disallowed import(s) {}", request.path(), disallowed);
                return FixProposal.notApplicable("Proposed import(s) outside the allowed list: " + disallowed);
            }

            if (proposal.replacement().isBlank()) {
                log.warn("Rejecting fix for {}: applicable but empty replacement", request.path());
                return FixProposal.notApplicable("Engine marked the fix applicable but returned an empty replacement.");
            }

            return proposal;

        } catch (Exception e) {
            log.error("Fix engine failed for {}; refusing", request.path(), e);
            return FixProposal.notApplicable("Fix engine failed: " + e.getClass().getSimpleName());
        }
    }
}
