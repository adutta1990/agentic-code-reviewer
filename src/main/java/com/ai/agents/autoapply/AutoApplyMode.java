package com.ai.agents.autoapply;

/**
 * Where a verified fix is sent. One pipeline, three sinks.
 *
 * <ul>
 *   <li>{@link #SUGGEST} — post a GitHub {@code ```suggestion} block a reviewer applies in one
 *       click. The safe default: it never mutates the branch, and the applied-vs-dismissed rate is
 *       a free precision metric.</li>
 *   <li>{@link #COMMIT} — commit the verified change to the PR branch and push.</li>
 *   <li>{@link #AUTOFIX_ONLY} — like COMMIT, but only for fixes whose behavior_change is NONE.
 *       A semantic change is never auto-committed; it falls through untouched.</li>
 * </ul>
 */
public enum AutoApplyMode {
    SUGGEST,
    COMMIT,
    AUTOFIX_ONLY
}
