package com.ai.agents.domain;

import java.util.List;

/**
 * The aggregated result of the triage → review pipeline for one PR.
 *
 * <p>Reports what every changed file was triaged as — including the files skipped and the
 * reason why — alongside the line-level findings for the files that were reviewed. Keeping the
 * skipped files and their triage reason in the response is deliberate: a reviewer looking at
 * the PR can see what the pipeline chose not to look at, not just what it flagged.
 *
 * @param repo          repository slug, echoed from the request.
 * @param filesChanged  total changed files in the PR.
 * @param filesReviewed how many were sent to the line-level reviewer.
 * @param totalFindings total findings posted across all reviewed files.
 * @param files         per-file outcomes, in the order the files were submitted.
 */
public record PullRequestReviewResult(
        String repo,
        int filesChanged,
        int filesReviewed,
        int totalFindings,
        List<FileOutcome> files) {

    public PullRequestReviewResult {
        files = files == null ? List.of() : List.copyOf(files);
    }

    /**
     * One file's journey through the pipeline: its triage verdict and, when it cleared the gate,
     * its review.
     *
     * @param path     repository-relative path.
     * @param reviewed true when the file was sent to the line-level reviewer.
     * @param triage   the triage verdict that decided whether to review.
     * @param review   the line-level review, or {@code null} when the file was skipped.
     */
    public record FileOutcome(
            String path,
            boolean reviewed,
            TriageVerdict triage,
            FileReviewResult review) {
    }
}
