package com.ai.agents.sandbox;

import com.ai.agents.sandbox.SandboxRunRequest.FileEdit;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Writes patch edits into the worktree, enforcing the path allowlist first.
 *
 * <p>Each edit is checked by {@link PathPolicy} (module-scoped, no build/CI files, no traversal)
 * and then by a defence-in-depth check that the resolved target really stays inside the worktree.
 * A target must already exist at the pinned SHA: the runner replaces existing files under review,
 * it does not let a patch introduce new files elsewhere. Since the worktree is pinned to the SHA,
 * that existing file is the fresh read at the commit the patch was computed against.
 */
@Component
public class PatchApplier {

    /**
     * Apply every edit, or stop at the first violation.
     *
     * @return the rejection reason, or empty if all edits were applied.
     */
    public Optional<String> apply(Path worktree, String moduleDir, List<FileEdit> edits) throws IOException {
        PathPolicy policy = new PathPolicy(moduleDir);
        Path root = worktree.toAbsolutePath().normalize();

        for (FileEdit edit : edits) {
            Optional<String> denied = policy.rejectionReason(edit.path());
            if (denied.isPresent()) {
                return Optional.of("blocked edit '" + edit.path() + "': " + denied.get());
            }

            Path target = root.resolve(edit.path()).normalize();
            if (!target.startsWith(root)) {
                return Optional.of("edit path escapes the worktree: " + edit.path());
            }
            if (!Files.isRegularFile(target)) {
                return Optional.of("target file does not exist at the pinned SHA: " + edit.path());
            }

            Files.writeString(target, edit.newContent(), StandardCharsets.UTF_8);
        }
        return Optional.empty();
    }
}
