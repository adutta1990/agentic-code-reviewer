package com.ai.agents.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * A request to mechanically fix one finding within a specific line range.
 *
 * <p>Carries the finding, the surrounding context the engine may reason over, and the two
 * constraints that keep a mechanically-applied patch safe: the exact set of imports the fix is
 * allowed to introduce and the file's indentation style. {@link #toUserPrompt()} renders the
 * user message; the allowed imports and indent style are injected into the system prompt.
 *
 * <p>{@code allowedImports} is the whitelist of fully-qualified imports the replacement may add.
 * It should include any standard-library imports the fix is permitted to use ({@code
 * java.util.List}, etc.) — an import absent from this list is rejected, because the output is
 * compiled without a human editing step.
 *
 * @param path                repository-relative path (for traceability/logging).
 * @param findingTitle        the finding to fix, from the reviewer.
 * @param findingExplanation  the reviewer's explanation of what breaks and why.
 * @param startLine           first line of the target range (inclusive, 1-based).
 * @param endLine             last line of the target range (inclusive, 1-based).
 * @param context             the surrounding code, line-numbered, that the engine may read.
 * @param existingImports     import statements already in the file.
 * @param allowedImports      fully-qualified imports the fix may introduce; anything else is rejected.
 * @param indentStyle         the file's indentation, e.g. {@code "4 spaces"}.
 */
public record FixRequest(
        String path,
        @NotBlank String findingTitle,
        String findingExplanation,
        @Positive int startLine,
        @Positive int endLine,
        String context,
        List<String> existingImports,
        List<String> allowedImports,
        String indentStyle) {

    public FixRequest {
        existingImports = existingImports == null ? List.of() : List.copyOf(existingImports);
        allowedImports = allowedImports == null ? List.of() : List.copyOf(allowedImports);
        indentStyle = (indentStyle == null || indentStyle.isBlank()) ? "4 spaces" : indentStyle;
        context = context == null ? "" : context;
    }

    /** Render the user message in the layout the refactoring system prompt expects. */
    public String toUserPrompt() {
        int ctxStart = Math.max(1, startLine - 40);
        int ctxEnd = endLine + 40;
        var sb = new StringBuilder();
        sb.append("Finding: ").append(orEmpty(findingTitle)).append('\n');
        sb.append("Explanation: ").append(orEmpty(findingExplanation)).append('\n');
        sb.append("Target range: lines ").append(startLine).append('-').append(endLine).append("\n\n");

        sb.append("--- SURROUNDING CONTEXT (lines ").append(ctxStart).append('-').append(ctxEnd).append(") ---\n");
        sb.append(context).append("\n\n");

        sb.append("--- EXISTING IMPORTS ---\n");
        sb.append(existingImports.isEmpty() ? "(none)" : String.join("\n", existingImports));
        return sb.toString();
    }

    /** Allowed-imports list for the system prompt, or an explicit "add nothing" when empty. */
    public String allowedImportsText() {
        return allowedImports.isEmpty() ? "(none — do not add any new import)" : String.join(", ", allowedImports);
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
