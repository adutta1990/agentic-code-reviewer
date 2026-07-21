package com.ai.agents.autoapply;

/**
 * Renders a GitHub review-comment body containing a {@code ```suggestion} block — the fenced kind
 * a reviewer can apply with one click. Pure and isolated so the exact wire format is unit-tested.
 */
public final class SuggestionRenderer {

    private SuggestionRenderer() {
    }

    /**
     * @param findingTitle short title shown above the suggestion, or null/blank to omit.
     * @param replacement  the code that replaces the commented lines.
     * @return the comment body with the suggestion fence.
     */
    public static String render(String findingTitle, String replacement) {
        var sb = new StringBuilder();
        if (findingTitle != null && !findingTitle.isBlank()) {
            sb.append("**").append(findingTitle.strip()).append("**\n\n");
        }
        sb.append("```suggestion\n");
        sb.append(stripTrailingNewline(replacement == null ? "" : replacement));
        sb.append("\n```");
        return sb.toString();
    }

    private static String stripTrailingNewline(String s) {
        String out = s;
        if (out.endsWith("\n")) {
            out = out.substring(0, out.length() - 1);
        }
        if (out.endsWith("\r")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }
}
