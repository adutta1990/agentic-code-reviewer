package com.ai.agents.autoapply;

import com.ai.agents.autoapply.VerifiedFixRequest.PrContext;
import com.ai.agents.config.AutoApplyProperties;
import com.ai.agents.sandbox.GitWorktree;
import com.ai.agents.sandbox.PatchApplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Commits a verified change to the PR branch and pushes it.
 *
 * <p>Guarded on two fronts. Push is off unless {@code allow-push} is enabled — so COMMIT mode
 * renders but does nothing external by default. And the branch tip must still equal the SHA the
 * fix was verified against: if the branch moved (a push or force-push landed since verification),
 * the commit is refused rather than layered onto a tree the sandbox never saw. The change is
 * re-applied through the same path allowlist as verification, in a throwaway worktree pinned to
 * the verified SHA, then pushed as a fast-forward.
 */
@Component
public class BranchCommitSink implements MutationSink {

    private static final Logger log = LoggerFactory.getLogger(BranchCommitSink.class);

    private final AutoApplyProperties props;
    private final GitWorktree git;
    private final PatchApplier applier;

    public BranchCommitSink(AutoApplyProperties props, GitWorktree git, PatchApplier applier) {
        this.props = props;
        this.git = git;
        this.applier = applier;
    }

    @Override
    public SinkResult publish(PublishContext ctx) {
        PrContext pr = ctx.pr();
        if (pr == null || pr.branch() == null || pr.branch().isBlank()) {
            return SinkResult.skipped("no PR branch; verified change not committed");
        }
        if (!props.isAllowPush()) {
            return SinkResult.skipped("push disabled (code-review.auto-apply.allow-push=false)");
        }

        Path repoDir = Paths.get(ctx.repoDir());
        Path worktreeRoot = null;
        try {
            // Force-push guard: only commit if the branch still points at the verified commit.
            String branchHead = git.refSha(repoDir, "refs/heads/" + pr.branch());
            if (branchHead.isBlank()) {
                branchHead = git.refSha(repoDir, pr.branch());
            }
            if (!ctx.sha().equals(branchHead)) {
                return SinkResult.failed("branch '" + pr.branch() + "' moved since verification (head="
                        + shortSha(branchHead) + ", verified=" + shortSha(ctx.sha()) + "); not committing");
            }

            worktreeRoot = Files.createTempDirectory("cr-commit-");
            Path checkout = worktreeRoot.resolve("repo");
            git.add(repoDir, ctx.sha(), checkout);

            Optional<String> denied = applier.apply(checkout, ctx.moduleDir(), List.of(ctx.edit()));
            if (denied.isPresent()) {
                return SinkResult.failed("edit blocked at commit time: " + denied.get());
            }

            String newSha = git.commitFileAndPush(
                    checkout, ctx.filePath(), pr.branch(),
                    props.getCommitAuthorName(), props.getCommitAuthorEmail(), ctx.commitMessage());

            log.info("Pushed verified fix to branch {} as {}", pr.branch(), shortSha(newSha));
            return SinkResult.published("committed and pushed to " + pr.branch(), newSha);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Commit/push failed for branch {}", pr.branch(), e);
            return SinkResult.failed("commit/push error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            cleanup(repoDir, worktreeRoot);
        }
    }

    private void cleanup(Path repoDir, Path worktreeRoot) {
        if (worktreeRoot == null) {
            return;
        }
        try {
            git.remove(repoDir, worktreeRoot.resolve("repo"));
        } catch (Exception e) {
            log.warn("worktree remove failed for {}", worktreeRoot, e);
        }
        deleteRecursively(worktreeRoot);
    }

    private static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }

    private static String shortSha(String sha) {
        return sha == null || sha.length() < 8 ? String.valueOf(sha) : sha.substring(0, 8);
    }
}
