package com.ai.agents.autoapply;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splices a fix engine's line-range replacement into a file's full text.
 *
 * <p>Pure and isolated because it is the one place an off-by-one silently corrupts a file. It
 * preserves the file's line separator (LF vs CRLF) and its trailing-newline presence, and
 * normalizes the replacement's own line endings to the file's. Line numbers are 1-based and
 * inclusive, matching how findings and the fix engine describe ranges.
 */
public final class PatchBuilder {

    private PatchBuilder() {
    }

    /**
     * Replace lines {@code [startLine, endLine]} of {@code original} with {@code replacement}.
     *
     * @throws IllegalArgumentException if the range is malformed or exceeds the file's length.
     */
    public static String splice(String original, int startLine, int endLine, String replacement) {
        if (startLine < 1 || endLine < startLine) {
            throw new IllegalArgumentException("invalid range " + startLine + ".." + endLine);
        }
        String separator = original.contains("\r\n") ? "\r\n" : "\n";
        boolean trailingNewline = original.endsWith(separator);

        List<String> lines = splitFileLines(original, separator);
        int lineCount = lines.size();
        if (endLine > lineCount) {
            throw new IllegalArgumentException(
                    "range end " + endLine + " exceeds file length " + lineCount);
        }

        List<String> out = new ArrayList<>(lines.subList(0, startLine - 1));
        out.addAll(splitReplacementLines(replacement));
        out.addAll(lines.subList(endLine, lineCount));

        String joined = String.join(separator, out);
        return trailingNewline ? joined + separator : joined;
    }

    /** File body into logical lines, dropping the single trailing separator if present. */
    private static List<String> splitFileLines(String content, String separator) {
        if (content.isEmpty()) {
            return new ArrayList<>();
        }
        String body = content.endsWith(separator)
                ? content.substring(0, content.length() - separator.length())
                : content;
        return new ArrayList<>(Arrays.asList(body.split(Pattern.quote(separator), -1)));
    }

    /** Replacement into lines, tolerant of LF or CRLF, dropping one trailing newline. Empty = deletion. */
    private static List<String> splitReplacementLines(String replacement) {
        if (replacement == null || replacement.isEmpty()) {
            return new ArrayList<>();
        }
        String body = replacement;
        if (body.endsWith("\n")) {
            body = body.substring(0, body.length() - 1);
        }
        if (body.endsWith("\r")) {
            body = body.substring(0, body.length() - 1);
        }
        return new ArrayList<>(Arrays.asList(body.split("\r?\n", -1)));
    }
}
