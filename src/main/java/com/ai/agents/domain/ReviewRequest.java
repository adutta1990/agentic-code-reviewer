package com.ai.agents.domain;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * One file submitted for line-level review.
 *
 * <p>Carries everything the reviewer prompt needs: the file's new revision, which lines the
 * PR actually changed, and the module context (Java version, frameworks, conventions) that
 * tells the reviewer what is idiomatic here. {@link #toPrompt()} numbers the file and renders
 * the exact user-message layout the review system prompt was written against.
 *
 * @param path          repository-relative path of the file under review.
 * @param javaVersion   Java language level of the module, e.g. {@code "17"}.
 * @param frameworks    frameworks in the module, e.g. {@code ["Spring Boot", "Spring AI"]}.
 * @param conventions   excerpt of the project's conventions the reviewer should honour.
 * @param fileContent   the file's full new revision, unnumbered (numbered on render).
 * @param changedRanges the line ranges the PR changed; the reviewer judges only these.
 */
public record ReviewRequest(
        @NotBlank String path,
        String javaVersion,
        List<String> frameworks,
        String conventions,
        @NotBlank String fileContent,
        List<LineRange> changedRanges) {

    public ReviewRequest {
        frameworks = frameworks == null ? List.of() : List.copyOf(frameworks);
        changedRanges = changedRanges == null ? List.of() : List.copyOf(changedRanges);
        fileContent = fileContent == null ? "" : fileContent;
    }

    /** Render the user message: header fields, the line-numbered file, then the changed ranges. */
    public String toPrompt() {
        var sb = new StringBuilder();
        sb.append("File: ").append(orEmpty(path)).append('\n');
        sb.append("Language: Java ").append(orEmpty(javaVersion)).append('\n');
        sb.append("Frameworks in module: ").append(String.join(", ", frameworks)).append('\n');
        sb.append("Project conventions:\n").append(orEmpty(conventions)).append("\n\n");

        sb.append("--- FILE CONTENT (with line numbers, new revision) ---\n");
        sb.append(numbered(fileContent)).append("\n\n");

        sb.append("--- CHANGED LINE RANGES ---\n");
        sb.append(changedRanges.isEmpty()
                ? "(none specified)"
                : changedRanges.stream().map(LineRange::render).reduce((a, b) -> a + ", " + b).orElse(""));
        sb.append("\n\nReview only defects introduced or made worse by the changed ranges.");
        return sb.toString();
    }

    /** Prefix each line with its 1-based number so the model can cite exact new-revision lines. */
    private static String numbered(String content) {
        String[] lines = content.split("\n", -1);
        var sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(String.format("%6d  %s", i + 1, lines[i]));
            if (i < lines.length - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
