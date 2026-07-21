package com.ai.agents.domain;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * The triage engine's verdict on one changed file: does it warrant deep review, and if so,
 * how risky is it and where should a reviewer look.
 *
 * <p>Spring AI derives a JSON schema from this record and binds the model's response into it
 * via {@code .entity()}. The {@link JsonPropertyDescription} annotations are serialized into
 * that schema and become part of the prompt — they are the instructions the model actually
 * follows when filling each field. Plain javadoc does not reach the model; only these
 * annotations do. The {@link JsonProperty} names pin the wire format to the snake_case schema
 * the system prompt specifies ({@code worth_review}, etc.).
 */
@JsonClassDescription("A triage decision for a single changed file in a Java pull request.")
public record TriageVerdict(

        @JsonProperty("path")
        @JsonPropertyDescription("The changed file's repository-relative path, copied exactly from the input.")
        String path,

        @JsonProperty("worth_review")
        @JsonPropertyDescription("""
                True if the file warrants deep human review. Generated code, lock files, and pure \
                import reordering are never worth review. When uncertain, set true — a false positive \
                is cheap here, a false negative is not.""")
        boolean worthReview,

        @JsonProperty("risk")
        @JsonPropertyDescription("HIGH if a defect here is severe or likely; MEDIUM if plausible; LOW for cosmetic or low-impact changes.")
        Risk risk,

        @JsonProperty("reason")
        @JsonPropertyDescription("Why this verdict, grounded only in the visible hunks. Maximum 20 words.")
        String reason,

        @JsonProperty("focus")
        @JsonPropertyDescription("""
                Aspects a reviewer should scrutinise, drawn only from what the hunks reveal. Empty when \
                worth_review is false.""")
        List<FocusArea> focus) {

    public TriageVerdict {
        focus = focus == null ? List.of() : List.copyOf(focus);
    }

    public enum Risk {
        LOW, MEDIUM, HIGH
    }

    public enum FocusArea {
        CORRECTNESS, CONCURRENCY, RESOURCE_LEAK, SECURITY, STYLE, TESTABILITY
    }
}
