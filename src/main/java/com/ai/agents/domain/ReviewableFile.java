package com.ai.agents.domain;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * A changed file carrying everything both analyzers need.
 *
 * <p>Triage judges the {@code diff}; the line-level reviewer judges the full {@code fileContent}
 * against the {@code changedRanges}. The orchestrator holds both so a single PR payload can feed
 * both stages without the caller sending the file twice.
 *
 * @param path          repository-relative path.
 * @param diff          unified diff for this file — the triage input.
 * @param fileContent   the file's full new revision — the review input.
 * @param changedRanges new-revision line ranges the PR changed — scopes the review.
 */
public record ReviewableFile(
        @NotBlank String path,
        String diff,
        String fileContent,
        List<LineRange> changedRanges) {

    public ReviewableFile {
        path = path == null ? "" : path.strip();
        diff = diff == null ? "" : diff;
        fileContent = fileContent == null ? "" : fileContent;
        changedRanges = changedRanges == null ? List.of() : List.copyOf(changedRanges);
    }
}
