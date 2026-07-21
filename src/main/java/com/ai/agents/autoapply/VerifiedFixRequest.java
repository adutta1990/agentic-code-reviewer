package com.ai.agents.autoapply;

import com.ai.agents.domain.FixProposal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * A request to verify one fix and route it to the configured sink.
 *
 * <p>The driver reads the target file fresh at {@code sha} (never trusting caller-supplied
 * content), splices the proposal's replacement into {@code [startLine, endLine]}, verifies the
 * result in the sandbox, and only on a green-to-green verdict publishes it.
 *
 * @param repoDir       absolute path to the warm source repository.
 * @param sha           commit the fix was computed against; verification and any commit anchor here.
 * @param moduleDir     module under review, repo-relative ({@code ""} = root).
 * @param filePath      repo-relative path of the file to patch.
 * @param startLine     first line the replacement covers (inclusive, 1-based).
 * @param endLine       last line the replacement covers (inclusive, 1-based).
 * @param findingTitle  the finding this fix addresses; used in the suggestion comment and commit message.
 * @param fixProposal   the proposal from the fix engine.
 * @param testClasses   affected tests to run in the sandbox.
 * @param mode          the sink to use; null falls back to the configured default.
 * @param pr            PR context for posting suggestions or pushing commits; may be null in render-only use.
 */
public record VerifiedFixRequest(
        @NotBlank String repoDir,
        @NotBlank String sha,
        String moduleDir,
        @NotBlank String filePath,
        @Positive int startLine,
        @Positive int endLine,
        String findingTitle,
        @NotNull FixProposal fixProposal,
        List<String> testClasses,
        AutoApplyMode mode,
        PrContext pr) {

    public VerifiedFixRequest {
        moduleDir = moduleDir == null ? "" : moduleDir.strip();
        testClasses = testClasses == null ? List.of() : List.copyOf(testClasses);
    }

    /**
     * The GitHub pull-request coordinates a sink needs.
     *
     * @param owner  repository owner/org.
     * @param repo   repository name.
     * @param number pull-request number.
     * @param branch the PR's head branch, e.g. {@code "feature/cache-invoices"}.
     */
    public record PrContext(String owner, String repo, int number, String branch) {
    }
}
