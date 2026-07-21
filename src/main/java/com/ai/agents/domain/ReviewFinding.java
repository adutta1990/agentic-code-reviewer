package com.ai.agents.domain;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * A single line-level review finding.
 *
 * <p>Spring AI derives a JSON schema from this record and binds the model's response into it
 * via {@code .entity()}. The {@link JsonPropertyDescription} annotations are serialized into
 * that schema and become part of the prompt — they are the instructions the model actually
 * follows when filling each field. The {@link JsonProperty} names pin the wire format to the
 * snake_case schema the system prompt specifies ({@code end_line}, {@code suggested_patch}).
 */
@JsonClassDescription("A single defensible, actionable defect found on a specific line of the reviewed file.")
public record ReviewFinding(

        @JsonProperty("line")
        @JsonPropertyDescription("The line number in the NEW file revision where the defect occurs.")
        int line,

        @JsonProperty("end_line")
        @JsonPropertyDescription("Last line of a multi-line finding, inclusive. Null when the finding is a single line.")
        Integer endLine,

        @JsonProperty("severity")
        @JsonPropertyDescription("BLOCKER must not merge; MAJOR a real defect that should be fixed; MINOR low-impact but genuine.")
        Severity severity,

        @JsonProperty("category")
        @JsonPropertyDescription("Short defect class, e.g. 'correctness', 'concurrency', 'resource-leak', 'security', 'api-contract'.")
        String category,

        @JsonProperty("title")
        @JsonPropertyDescription("A specific one-line summary of the defect. Maximum 10 words.")
        String title,

        @JsonProperty("explanation")
        @JsonPropertyDescription("Two to four sentences: what breaks, under what conditions, and why it matters. Cite the exact code path.")
        String explanation,

        @JsonProperty("suggested_patch")
        @JsonPropertyDescription("""
                Exact replacement text for lines line..end_line, correctly indented and with no diff \
                markers. Null when no mechanical fix applies.""")
        String suggestedPatch,

        @JsonProperty("confidence")
        @JsonPropertyDescription("Your confidence this is a real, defensible defect, 0.0 to 1.0. Do not emit findings below 0.6.")
        double confidence) {

    public enum Severity {
        BLOCKER, MAJOR, MINOR
    }
}
