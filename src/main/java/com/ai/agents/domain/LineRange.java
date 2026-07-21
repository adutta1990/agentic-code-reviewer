package com.ai.agents.domain;

/**
 * An inclusive range of new-revision line numbers changed by the PR.
 *
 * <p>A single changed line is expressed as {@code start == end}. The reviewer is told to flag
 * only defects introduced or worsened within these ranges.
 *
 * @param start first changed line (1-based, inclusive).
 * @param end   last changed line (1-based, inclusive); equals {@code start} for a single line.
 */
public record LineRange(int start, int end) {

    public LineRange {
        if (end < start) {
            int tmp = start;
            start = end;
            end = tmp;
        }
    }

    /** Rendered as {@code "10"} for a single line or {@code "10-15"} for a span. */
    public String render() {
        return start == end ? Integer.toString(start) : start + "-" + end;
    }
}
