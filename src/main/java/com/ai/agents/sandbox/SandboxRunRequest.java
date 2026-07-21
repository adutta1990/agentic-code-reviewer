package com.ai.agents.sandbox;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * A request to verify a patch against a repo at a specific commit, with no LLM in the loop.
 *
 * <p>The runner pins a worktree to {@code sha}, records the baseline test results, applies the
 * {@code edits}, and re-runs — accepting the patch only if the build goes green-to-green. Pinning
 * to an exact SHA is what makes a concurrent force-push to the branch harmless: the verification
 * always runs against the commit the patch was computed for.
 *
 * @param repoDir     absolute path to the source git repository (already on disk, warm).
 * @param sha         the commit the patch was computed against; the worktree is pinned here.
 * @param moduleDir   module under review, repo-relative ({@code ""} = repo root). Scopes both the
 *                    build ({@code -pl}) and the path allowlist.
 * @param edits       the patch as full-file replacements — unambiguous to apply and verify.
 * @param testClasses fully-qualified test classes to run (the subset touching the changed code).
 *                    Empty runs compile-only verification.
 */
public record SandboxRunRequest(
        @NotBlank String repoDir,
        @NotBlank String sha,
        String moduleDir,
        @NotNull List<@Valid FileEdit> edits,
        List<String> testClasses) {

    public SandboxRunRequest {
        moduleDir = moduleDir == null ? "" : moduleDir.strip();
        edits = edits == null ? List.of() : List.copyOf(edits);
        testClasses = testClasses == null ? List.of() : List.copyOf(testClasses);
    }

    /**
     * One file's replacement. Full new content rather than a unified diff: deterministic to apply
     * (no fuzzy hunk matching) and the green-to-green criterion does not care how the patch formed.
     *
     * @param path       repo-relative path of the file to overwrite.
     * @param newContent the complete new file content.
     */
    public record FileEdit(@NotBlank String path, String newContent) {
        public FileEdit {
            path = path == null ? "" : path.strip();
            newContent = newContent == null ? "" : newContent;
        }
    }
}
