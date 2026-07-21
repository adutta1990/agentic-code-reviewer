package com.ai.agents.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * A pull request submitted for triage.
 *
 * <p>Mirrors the fields a CI hook or GitHub webhook already has on hand when a PR opens.
 * {@link #toPrompt()} renders these into the exact user-message layout the triage system
 * prompt expects, so the controller/service never has to hand-format the model input.
 *
 * @param repo         repository slug, e.g. {@code acme/billing-service}.
 * @param baseRef      branch the PR merges into, e.g. {@code main}.
 * @param prTitle      the PR title.
 * @param prBody       the PR description. May be blank.
 * @param changedFiles the changed files with their unified diffs — the only thing judged.
 */
public record PullRequest(
        String repo,
        String baseRef,
        String prTitle,
        String prBody,
        @NotEmpty List<@Valid ChangedFile> changedFiles) {

    public PullRequest {
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
    }

    /**
     * Render the PR into the user message. The layout matches the template the system prompt
     * was written against: header fields first, then one labelled diff block per changed file.
     */
    public String toPrompt() {
        var sb = new StringBuilder();
        sb.append("Repository: ").append(orEmpty(repo)).append('\n');
        sb.append("Base branch: ").append(orEmpty(baseRef)).append('\n');
        sb.append("PR title: ").append(orEmpty(prTitle)).append('\n');
        sb.append("PR description: ").append(orEmpty(prBody)).append("\n\n");
        sb.append("Changed files with unified diffs:\n");
        for (ChangedFile file : changedFiles) {
            sb.append("\n### ").append(file.path()).append('\n');
            sb.append("```diff\n").append(file.diff()).append("\n```\n");
        }
        return sb.toString().strip();
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}