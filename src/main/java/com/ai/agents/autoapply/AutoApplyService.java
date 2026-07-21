package com.ai.agents.autoapply;

import com.ai.agents.autoapply.VerifiedFixResult.Status;
import com.ai.agents.config.AutoApplyProperties;
import com.ai.agents.domain.FixProposal;
import com.ai.agents.sandbox.SandboxRunRequest;
import com.ai.agents.sandbox.SandboxRunRequest.FileEdit;
import com.ai.agents.sandbox.SandboxResult;
import com.ai.agents.sandbox.SandboxRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.List;

/**
 * The verified auto-apply driver: turn a fix proposal into a patch, verify it in the sandbox, and
 * publish it only if the build stays green — routed to the sink the mode selects.
 *
 * <p>The policy, in order:
 * <ol>
 *   <li>An inapplicable proposal is gated before any work.</li>
 *   <li>In AUTOFIX_ONLY, a proposal that changes behavior is gated — semantic changes are never
 *       auto-committed.</li>
 *   <li>The file is read fresh at the SHA, the replacement is spliced in, and the sandbox verifies
 *       the result.</li>
 *   <li>REJECTED (won't compile) and ESCALATE (suspicious) never publish. Only ACCEPTED — green to
 *       green — reaches a sink: SUGGEST posts a suggestion, COMMIT/AUTOFIX_ONLY commit and push.</li>
 * </ol>
 * The driver never throws for expected failures; every path returns a {@link VerifiedFixResult}.
 */
@Service
public class AutoApplyService {

    private static final Logger log = LoggerFactory.getLogger(AutoApplyService.class);

    private final SandboxRunner sandbox;
    private final GitFileReader fileReader;
    private final GitHubSuggestionSink suggestionSink;
    private final BranchCommitSink commitSink;
    private final AutoApplyProperties props;

    public AutoApplyService(SandboxRunner sandbox, GitFileReader fileReader,
                            GitHubSuggestionSink suggestionSink, BranchCommitSink commitSink,
                            AutoApplyProperties props) {
        this.sandbox = sandbox;
        this.fileReader = fileReader;
        this.suggestionSink = suggestionSink;
        this.commitSink = commitSink;
        this.props = props;
    }

    public VerifiedFixResult apply(VerifiedFixRequest req) {
        AutoApplyMode mode = req.mode() != null ? req.mode() : props.getMode();
        FixProposal proposal = req.fixProposal();

        if (proposal == null || !proposal.applicable()) {
            return gated(Status.GATED_NOT_APPLICABLE, mode, "fix proposal is not applicable");
        }
        if (mode == AutoApplyMode.AUTOFIX_ONLY
                && proposal.behaviorChange() != FixProposal.BehaviorChange.NONE) {
            return gated(Status.GATED_BEHAVIOR_CHANGE, mode,
                    "behavior_change=" + proposal.behaviorChange() + " is not eligible for AUTOFIX_ONLY");
        }

        String suggestionBody = SuggestionRenderer.render(req.findingTitle(), proposal.replacement());

        FileEdit edit;
        try {
            String original = fileReader.read(Paths.get(req.repoDir()), req.sha(), req.filePath());
            String patched = PatchBuilder.splice(original, req.startLine(), req.endLine(), proposal.replacement());
            edit = new FileEdit(req.filePath(), patched);
        } catch (IllegalArgumentException e) {
            return error(mode, suggestionBody, "cannot build patch: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to read/splice {} at {}", req.filePath(), req.sha(), e);
            return error(mode, suggestionBody, "cannot read file at SHA: " + e.getMessage());
        }

        SandboxResult sr = sandbox.run(new SandboxRunRequest(
                req.repoDir(), req.sha(), req.moduleDir(), List.of(edit), req.testClasses()));

        return switch (sr.verdict()) {
            case REJECTED -> new VerifiedFixResult(Status.REJECTED_BUILD, mode, sr.verdict(),
                    "patched code does not compile: " + sr.reason(), suggestionBody, sr, null);
            case ESCALATE -> new VerifiedFixResult(Status.ESCALATED, mode, sr.verdict(),
                    sr.reason(), suggestionBody, sr, null);
            case ACCEPTED -> publish(req, mode, edit, suggestionBody, sr);
        };
    }

    private VerifiedFixResult publish(VerifiedFixRequest req, AutoApplyMode mode, FileEdit edit,
                                      String suggestionBody, SandboxResult sr) {
        PublishContext ctx = new PublishContext(
                req.pr(), req.filePath(), req.moduleDir(), req.startLine(), req.endLine(),
                edit, req.sha(), req.repoDir(), suggestionBody, commitMessage(req));

        MutationSink sink = mode == AutoApplyMode.SUGGEST ? suggestionSink : commitSink;
        SinkResult result = sink.publish(ctx);

        Status status = switch (result.status()) {
            case PUBLISHED -> mode == AutoApplyMode.SUGGEST ? Status.PUBLISHED_SUGGESTION : Status.COMMITTED;
            case SKIPPED -> Status.SINK_SKIPPED;
            case FAILED -> Status.SINK_FAILED;
        };
        return new VerifiedFixResult(status, mode, sr.verdict(), result.detail(), suggestionBody, sr, result);
    }

    private String commitMessage(VerifiedFixRequest req) {
        String title = req.findingTitle() == null || req.findingTitle().isBlank()
                ? req.filePath()
                : req.findingTitle().strip();
        return props.getCommitMessagePrefix() + title;
    }

    private static VerifiedFixResult gated(Status status, AutoApplyMode mode, String reason) {
        return new VerifiedFixResult(status, mode, null, reason, null, null, null);
    }

    private static VerifiedFixResult error(AutoApplyMode mode, String suggestionBody, String reason) {
        return new VerifiedFixResult(Status.ERROR, mode, null, reason, suggestionBody, null, null);
    }
}
