package com.ai.agents.sandbox;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Thin wrapper over {@code git worktree}. A worktree pinned to an exact SHA gives each run an
 * isolated checkout that shares the source repo's object store — cheaper than a full clone and,
 * because it is detached at a fixed commit, immune to branch movement during the run.
 */
@Component
public class GitWorktree {

    private static final int GIT_TIMEOUT_SECONDS = 60;
    private static final int GIT_OUTPUT_CHARS = 8_000;

    /** True if {@code sha} resolves to a commit in {@code repoDir}. */
    public boolean commitExists(Path repoDir, String sha) throws IOException, InterruptedException {
        return git(repoDir, List.of("cat-file", "-e", sha + "^{commit}")).exitCode() == 0;
    }

    /** The current HEAD commit of {@code repoDir}, or empty string if it cannot be read. */
    public String headSha(Path repoDir) throws IOException, InterruptedException {
        Processes.Exec e = git(repoDir, List.of("rev-parse", "HEAD"));
        return e.exitCode() == 0 ? e.output().strip() : "";
    }

    /** The commit a ref resolves to (e.g. a branch name), or empty string if it cannot be read. */
    public String refSha(Path repoDir, String ref) throws IOException, InterruptedException {
        Processes.Exec e = git(repoDir, List.of("rev-parse", ref));
        return e.exitCode() == 0 ? e.output().strip() : "";
    }

    /**
     * Stage {@code filePath}, commit it with the given identity, push to {@code branch}, and
     * return the new commit SHA. Runs inside a worktree checkout; a linked worktree shares the
     * source repo's remotes, so {@code push origin} works. Throws if any step fails.
     */
    public String commitFileAndPush(Path worktreeCheckout, String filePath, String branch,
                                    String authorName, String authorEmail, String message)
            throws IOException, InterruptedException {
        require(git(worktreeCheckout, List.of("add", "--", filePath)), "git add");
        require(git(worktreeCheckout, List.of(
                "-c", "user.name=" + authorName,
                "-c", "user.email=" + authorEmail,
                "commit", "-m", message)), "git commit");
        require(git(worktreeCheckout, List.of(
                "push", "origin", "HEAD:refs/heads/" + branch)), "git push");
        Processes.Exec head = git(worktreeCheckout, List.of("rev-parse", "HEAD"));
        require(head, "git rev-parse");
        return head.output().strip();
    }

    private static void require(Processes.Exec e, String what) throws IOException {
        if (e.exitCode() != 0) {
            throw new IOException(what + " failed: " + e.output());
        }
    }

    /** Add a detached worktree at {@code sha}. Throws if git fails (e.g. the SHA was GC'd away). */
    public void add(Path repoDir, String sha, Path worktreeDir) throws IOException, InterruptedException {
        Processes.Exec e = git(repoDir,
                List.of("worktree", "add", "--detach", worktreeDir.toString(), sha));
        if (e.exitCode() != 0) {
            throw new IOException("git worktree add failed for " + sha + ": " + e.output());
        }
    }

    /** Remove the worktree and prune the registry. Best-effort; failures are the caller's to log. */
    public void remove(Path repoDir, Path worktreeDir) throws IOException, InterruptedException {
        git(repoDir, List.of("worktree", "remove", "--force", worktreeDir.toString()));
        git(repoDir, List.of("worktree", "prune"));
    }

    private Processes.Exec git(Path repoDir, List<String> args) throws IOException, InterruptedException {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add("git");
        cmd.add("-C");
        cmd.add(repoDir.toString());
        cmd.addAll(args);
        return Processes.run(cmd, repoDir, null, GIT_TIMEOUT_SECONDS, GIT_OUTPUT_CHARS);
    }
}
