package com.ai.agents.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * A whole pull request submitted for the triage → review pipeline.
 *
 * <p>Holds the PR metadata, the module context shared by every file (Java version, frameworks,
 * conventions), and the changed files. The projection methods build the inputs the two stages
 * already expect, so the orchestrator never reshapes payloads inline.
 *
 * @param repo        repository slug.
 * @param baseRef     branch the PR merges into.
 * @param prTitle     the PR title.
 * @param prBody      the PR description.
 * @param javaVersion Java language level of the module, e.g. {@code "17"}.
 * @param frameworks  frameworks in the module.
 * @param conventions project conventions excerpt handed to the reviewer.
 * @param files       the changed files, each with diff, content, and changed ranges.
 */
public record PullRequestReviewRequest(
        String repo,
        String baseRef,
        String prTitle,
        String prBody,
        String javaVersion,
        List<String> frameworks,
        String conventions,
        @NotEmpty List<@Valid ReviewableFile> files) {

    public PullRequestReviewRequest {
        frameworks = frameworks == null ? List.of() : List.copyOf(frameworks);
        files = files == null ? List.of() : List.copyOf(files);
    }

    /** The triage input: PR metadata plus one {@link ChangedFile} (path + diff) per file. */
    public PullRequest toPullRequest() {
        List<ChangedFile> changed = files.stream()
                .map(f -> new ChangedFile(f.path(), f.diff()))
                .toList();
        return new PullRequest(repo, baseRef, prTitle, prBody, changed);
    }

    /** The review input for one file: the file's content and ranges plus the shared module context. */
    public ReviewRequest toReviewRequest(ReviewableFile file) {
        return new ReviewRequest(
                file.path(), javaVersion, frameworks, conventions, file.fileContent(), file.changedRanges());
    }
}
