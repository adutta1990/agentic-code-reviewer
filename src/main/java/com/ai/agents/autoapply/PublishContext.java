package com.ai.agents.autoapply;

import com.ai.agents.autoapply.VerifiedFixRequest.PrContext;
import com.ai.agents.sandbox.SandboxRunRequest.FileEdit;

/**
 * Everything a sink needs to publish one verified fix. Assembled by the driver after verification.
 *
 * @param pr             PR coordinates (may be null in render-only use).
 * @param filePath       repo-relative path being changed.
 * @param moduleDir      module under review, repo-relative; scopes the commit-time path allowlist.
 * @param startLine      first line of the change (for multi-line suggestion anchoring).
 * @param endLine        last line of the change (the comment anchor line).
 * @param edit           the full-file edit the change produces.
 * @param sha            the verified base commit; a commit sink anchors and guards against this.
 * @param repoDir        absolute path to the source repository.
 * @param suggestionBody the rendered suggestion comment body.
 * @param commitMessage  the commit message a commit sink should use.
 */
public record PublishContext(
        PrContext pr,
        String filePath,
        String moduleDir,
        int startLine,
        int endLine,
        FileEdit edit,
        String sha,
        String repoDir,
        String suggestionBody,
        String commitMessage) {
}
