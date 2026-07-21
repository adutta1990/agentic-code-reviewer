package com.ai.agents.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * A single file changed by a pull request, together with its unified diff.
 *
 * <p>The triage engine judges <em>only</em> what is in {@code diff} — the hunks it can
 * actually see. It never infers the rest of the file's contents. Keep {@code diff} as the
 * raw unified diff (the {@code @@ ... @@} hunks with {@code +}/{@code -} lines); that is the
 * exact form the model is told to reason over.
 *
 * @param path repository-relative path, e.g. {@code src/main/java/com/acme/Foo.java}.
 * @param diff the file's unified diff for this PR. May be truncated for very large files.
 */
public record ChangedFile(@NotBlank String path, String diff) {

    public ChangedFile {
        path = path == null ? "" : path.strip();
        diff = diff == null ? "" : diff;
    }
}