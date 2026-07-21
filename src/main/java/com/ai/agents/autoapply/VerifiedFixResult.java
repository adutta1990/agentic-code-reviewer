package com.ai.agents.autoapply;

import com.ai.agents.sandbox.SandboxResult;

/**
 * The outcome of one verified-fix attempt: what the driver decided and why.
 *
 * <p>{@code suggestion} carries the rendered {@code ```suggestion} block whenever the driver got
 * far enough to build one, even if the sink did not post it — so a caller can post it itself.
 * {@code sandbox} is the full verification evidence, present once the sandbox ran.
 *
 * @param status     the terminal state of the attempt.
 * @param mode       the sink the attempt used.
 * @param verdict    the sandbox verdict, or null if the attempt was gated before verification.
 * @param reason     a human-readable explanation.
 * @param suggestion the rendered suggestion block, or null if none was built.
 * @param sandbox    the full sandbox result, or null if not run.
 * @param sink       what the sink did, or null if no sink was invoked.
 */
public record VerifiedFixResult(
        Status status,
        AutoApplyMode mode,
        SandboxResult.Verdict verdict,
        String reason,
        String suggestion,
        SandboxResult sandbox,
        SinkResult sink) {

    /** Whether the attempt produced an externally-visible change or suggestion. */
    public boolean published() {
        return status == Status.PUBLISHED_SUGGESTION || status == Status.COMMITTED;
    }

    public enum Status {
        /** A suggestion comment was posted (or was ready to post; see {@link #sink}). */
        PUBLISHED_SUGGESTION,
        /** The verified change was committed and pushed to the PR branch. */
        COMMITTED,
        /** The patched code did not compile; nothing published. */
        REJECTED_BUILD,
        /** The build was suspicious (baseline red, test-set changed, timeout); a human must decide. */
        ESCALATED,
        /** The fix proposal was not applicable; nothing attempted. */
        GATED_NOT_APPLICABLE,
        /** AUTOFIX_ONLY mode and the fix changes behavior; not eligible for auto-commit. */
        GATED_BEHAVIOR_CHANGE,
        /** Verified green, but the sink was not configured to publish (e.g. no token, push disabled). */
        SINK_SKIPPED,
        /** Verified green, but publishing failed (network, git, GitHub API). */
        SINK_FAILED,
        /** The driver itself failed (bad range, unreadable file, unexpected error). */
        ERROR
    }
}
