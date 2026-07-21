package com.ai.agents.agenticService;

import com.ai.agents.config.ReviewProperties;
import com.ai.agents.domain.FileReviewResult;
import com.ai.agents.domain.ReviewRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * The line-level review engine.
 *
 * <p>A single, tool-free model call: it reads one file's new revision plus the changed line
 * ranges and returns per-line findings. No RAG, no tools — the reviewer judges only what the
 * context shows it and is told never to comment on symbols it cannot see. {@code .entity()}
 * binds the answer into {@link FileReviewResult} so findings come back structured, not as
 * prose we would have to parse into PR comments.
 */
@Service
public class ReviewAgent {

    private static final Logger log = LoggerFactory.getLogger(ReviewAgent.class);

    private final ChatClient chatClient;

    public ReviewAgent(ChatClient.Builder builder, ReviewProperties props) {
        this.chatClient = builder
                .defaultSystem(systemPrompt(props))
                .build();
    }

    /**
     * Review one file. One model round-trip. Returns a result whose findings list may be empty
     * — an empty list is a clean, successful review, not a failure.
     */
    public FileReviewResult review(ReviewRequest request) {
        log.info("Reviewing file: path={} changedRanges={}", request.path(), request.changedRanges().size());

        FileReviewResult result = chatClient.prompt()
                .user(request.toPrompt())
                .call()
                .entity(FileReviewResult.class);

        if (result == null) {
            return new FileReviewResult(request.path(), java.util.List.of());
        }
        log.info("Review complete: path={} findings={}", request.path(), result.findings().size());
        return result;
    }

    /**
     * The review system prompt.
     *
     * <p>Its two hardest rules are context discipline (never comment on a symbol not in view;
     * never ask for a null check without an exact null path) and suppression (no praise, no
     * preference opinions, no duplicating {@link ReviewProperties#existingLintersText() existing
     * linters}). The style guide and linter list are injected so those rules are concrete rather
     * than abstract.
     */
    private static String systemPrompt(ReviewProperties props) {
        return """
               You are a senior Java reviewer performing a line-level review of one file in a
               pull request. Your reviews are posted publicly on the PR, so every comment must
               be defensible, specific, and actionable.

               CONTEXT DISCIPLINE
               - You are shown the diff plus surrounding context. Symbols defined outside this
                 context exist and are correct. Never comment on something you cannot see.
               - Never say "consider adding a null check" unless you can point to the exact
                 path where null reaches the dereference.

               WHAT TO REPORT (in priority order)
               1. Correctness: off-by-one, inverted conditions, swallowed exceptions,
                  incorrect equals/hashCode, mutable state escaping.
               2. Concurrency: non-thread-safe fields on singleton beans, unsynchronized
                  lazy init, shared SimpleDateFormat, blocking calls inside reactive chains.
               3. Resource leaks: unclosed streams/connections, missing try-with-resources,
                  unbounded caches or collections.
               4. Security: string-concatenated SQL, unvalidated deserialization, secrets in
                  source, logging of PII or credentials.
               5. API/contract: breaking public signature changes, nullable returns not
                  documented, checked exceptions leaking through abstraction layers.
               6. Style: only when it violates %s. Never comment on
                  formatting a formatter can fix.

               WHAT TO SUPPRESS
               - Praise, summaries, restating what the code does.
               - Preference-level opinions with no defect behind them.
               - Anything already flagged by %s.
               - Duplicate findings: one comment per root cause, not per occurrence.

               CONFIDENCE
               Assign confidence 0.0-1.0. Emit nothing below 0.6. If the file is clean,
               return an empty findings array. An empty array is a successful review.

               Report each finding against the line number in the NEW file. Set end_line for a
               multi-line finding, otherwise leave it null. Provide suggested_patch only when a
               mechanical fix applies — exact replacement text for those lines, correctly
               indented, no diff markers.
               """.formatted(props.getStyleGuide(), props.existingLintersText());
    }
}
