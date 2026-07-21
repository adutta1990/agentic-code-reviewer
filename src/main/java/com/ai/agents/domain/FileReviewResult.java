package com.ai.agents.domain;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * The line-level review of one file: its path plus every finding worth posting.
 *
 * <p>An empty {@code findings} list is a successful review — it means the changed ranges are
 * clean, not that the reviewer failed.
 */
@JsonClassDescription("The complete line-level review of a single file: its path and all findings.")
public record FileReviewResult(

        @JsonProperty("path")
        @JsonPropertyDescription("The reviewed file's repository-relative path, copied exactly from the input.")
        String path,

        @JsonProperty("findings")
        @JsonPropertyDescription("Every defect found in the changed ranges. Empty when the file is clean.")
        List<ReviewFinding> findings) {

    public FileReviewResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
