package com.ai.agents.domain;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * A minimal, mechanically-applicable replacement for one line range.
 *
 * <p>Spring AI derives a JSON schema from this record and binds the model's response into it
 * via {@code .entity()}. The {@link JsonPropertyDescription} annotations are serialized into
 * that schema and become the instructions the model follows per field. The {@link JsonProperty}
 * names pin the wire format to the snake_case schema the system prompt specifies.
 *
 * <p>When {@link #applicable()} is false the patch must not be applied: {@link #replacement()}
 * is empty and {@link #reasonIfNot()} explains why the fix could not be made within the range.
 */
@JsonClassDescription("A single minimal replacement for a specific line range, applied without human editing.")
public record FixProposal(

        @JsonProperty("applicable")
        @JsonPropertyDescription("""
                True only if the finding can be fully fixed within the target range without breaking \
                callers. False means apply nothing.""")
        boolean applicable,

        @JsonProperty("reason_if_not")
        @JsonPropertyDescription("Why the fix cannot be made within the range. Null when applicable is true.")
        String reasonIfNot,

        @JsonProperty("new_imports")
        @JsonPropertyDescription("""
                Fully-qualified imports the replacement requires that are not already present. Only \
                imports from the allowed list. Empty if none.""")
        List<String> newImports,

        @JsonProperty("replacement")
        @JsonPropertyDescription("""
                The full replacement text for the target lines only — not the whole file, not a diff. \
                Correctly indented, existing comments and Javadoc preserved. Empty when not applicable.""")
        String replacement,

        @JsonProperty("behavior_change")
        @JsonPropertyDescription("""
                NONE if observable behaviour is identical; PERFORMANCE_ONLY if only performance differs; \
                SEMANTIC if observable behaviour changes.""")
        BehaviorChange behaviorChange,

        @JsonProperty("risk_note")
        @JsonPropertyDescription("Any residual risk a human should know before merge. Null if none.")
        String riskNote) {

    public FixProposal {
        newImports = newImports == null ? List.of() : List.copyOf(newImports);
    }

    /** A refusal: apply nothing. Used when the fix is rejected before or after the model call. */
    public static FixProposal notApplicable(String reason) {
        return new FixProposal(false, reason, List.of(), "", BehaviorChange.NONE, null);
    }

    public enum BehaviorChange {
        NONE, SEMANTIC, PERFORMANCE_ONLY
    }
}
