package com.ai.agents.agenticService;

import com.ai.agents.domain.FixProposal;
import com.ai.agents.domain.FixRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * The mechanical fix engine.
 *
 * <p>A single, tool-free model call that turns one finding into a minimal replacement for a
 * specific line range. The system prompt is built per request because two of its hard
 * constraints — the file's indentation style and the allowed-imports whitelist — vary per
 * file, so they are injected rather than baked into a static default system prompt.
 * {@code .entity()} binds the answer into {@link FixProposal}; the constraints are enforced
 * again deterministically downstream, since the output is compiled without a human editing step.
 */
@Service
public class FixAgent {

    private static final Logger log = LoggerFactory.getLogger(FixAgent.class);

    private final ChatClient chatClient;

    public FixAgent(ChatClient.Builder builder) {
        // No default system prompt: it is assembled per request from the file's constraints.
        this.chatClient = builder.build();
    }

    /** Propose a fix. One model round-trip. Never returns null. */
    public FixProposal propose(FixRequest request) {
        log.info("Proposing fix: path={} finding='{}' range={}-{}",
                request.path(), request.findingTitle(), request.startLine(), request.endLine());

        FixProposal proposal = chatClient.prompt()
                .system(systemPrompt(request))
                .user(request.toUserPrompt())
                .call()
                .entity(FixProposal.class);

        if (proposal == null) {
            return FixProposal.notApplicable("The engine returned no proposal.");
        }
        log.info("Fix proposal: path={} applicable={} behaviorChange={}",
                request.path(), proposal.applicable(), proposal.behaviorChange());
        return proposal;
    }

    /**
     * The refactoring system prompt, with the file's indentation and allowed-imports whitelist
     * injected. The remaining constraints — minimality, signature preservation, comment
     * preservation, no partial fixes — are constant.
     */
    private static String systemPrompt(FixRequest r) {
        return """
               You are a Java refactoring engine. You produce a single, minimal, compilable
               replacement for a specific line range. Your output is applied mechanically —
               there is no human editing step before compilation.

               HARD CONSTRAINTS
               - Change ONLY what is required to fix the stated finding.
               - Preserve the public signature of every method unless the finding is
                 explicitly about that signature.
               - Preserve existing comments and Javadoc inside the range.
               - Match surrounding indentation exactly (the file uses %s).
               - Do not introduce a dependency not present in this list: %s.
               - Do not rename anything not required by the fix.
               - If the fix cannot be made within the given range without breaking callers,
                 set applicable=false and explain. Do not attempt a partial fix.

               Produce the full replacement text for lines %d..%d only — not the whole file and
               not a diff. When you cannot fix the finding within that range without breaking
               callers, set applicable=false, give the reason, and return an empty replacement.
               """.formatted(r.indentStyle(), r.allowedImportsText(), r.startLine(), r.endLine());
    }
}
