package com.ai.agents.sandbox;

import com.ai.agents.sandbox.SandboxResult.BuildOutcome;
import com.ai.agents.sandbox.SandboxResult.TestDelta;
import com.ai.agents.sandbox.SandboxResult.TestStatus;
import com.ai.agents.sandbox.SandboxResult.Verdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * The verified-apply sandbox: take a patch and a repo SHA, return green/red plus the test delta,
 * with no LLM anywhere in the loop. This is the piece the whole auto-apply story rests on, so it
 * is deliberately buildable and testable on its own.
 *
 * <p>Each run pins a worktree to the SHA, records the baseline (compile + affected test subset),
 * applies the patch, and re-runs. The verdict:
 * <ul>
 *   <li><b>ACCEPTED</b> — baseline green, candidate green, and the test result set is identical.</li>
 *   <li><b>REJECTED</b> — the patched code does not compile. Discard; no human needed.</li>
 *   <li><b>ESCALATE</b> — anything suspicious: the baseline was not green, the test set changed in
 *       either direction (a broken pass <em>or</em> a suspiciously-fixed failure), a build timed
 *       out, or the runner errored. A human decides.</li>
 * </ul>
 *
 * <p>Guardrails enforced here: a temp worktree deleted in {@code finally}, a global concurrency
 * cap via a fair semaphore, and a queue timeout so a request does not block forever waiting for a
 * build permit. The per-build wall-clock timeout, offline mode, and path allowlist live in the
 * collaborators this class drives.
 */
@Service
public class SandboxRunner {

    private static final Logger log = LoggerFactory.getLogger(SandboxRunner.class);

    private final SandboxProperties props;
    private final GitWorktree git;
    private final MavenRunner maven;
    private final SurefireReportParser surefire;
    private final PatchApplier applier;
    private final Semaphore buildPermits;

    public SandboxRunner(SandboxProperties props, GitWorktree git, MavenRunner maven,
                         SurefireReportParser surefire, PatchApplier applier) {
        this.props = props;
        this.git = git;
        this.maven = maven;
        this.surefire = surefire;
        this.applier = applier;
        this.buildPermits = new Semaphore(Math.max(1, props.getMaxConcurrentBuilds()), true);
    }

    public SandboxResult run(SandboxRunRequest req) {
        long start = System.currentTimeMillis();
        Path repoDir = Paths.get(req.repoDir());

        try {
            if (!git.commitExists(repoDir, req.sha())) {
                return done(Verdict.ESCALATE, "unknown or garbage-collected SHA: " + req.sha(),
                        null, null, List.of(), start);
            }
        } catch (IOException | InterruptedException e) {
            return runnerError(e, start);
        }

        boolean acquired;
        try {
            acquired = buildPermits.tryAcquire(props.getQueueTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return done(Verdict.ESCALATE, "interrupted while queuing for a build permit",
                    null, null, List.of(), start);
        }
        if (!acquired) {
            return done(Verdict.ESCALATE, "sandbox busy: no build permit within "
                    + props.getQueueTimeoutSeconds() + "s", null, null, List.of(), start);
        }

        Path worktree = null;
        try {
            worktree = Files.createTempDirectory(Paths.get(props.getWorkRoot()), "cr-sbx-");
            // git worktree add requires the target dir not pre-exist; hand it a fresh child path.
            Path checkout = worktree.resolve("repo");
            git.add(repoDir, req.sha(), checkout);

            BuildOutcome baseline = build(checkout, req);
            if (!baseline.compiled()) {
                String why = baseline.timedOut()
                        ? "baseline build timed out; cannot establish a green starting point"
                        : "baseline does not compile at the SHA; cannot verify against it";
                return done(Verdict.ESCALATE, why, baseline, null, List.of(), start);
            }

            java.util.Optional<String> applyError = applier.apply(checkout, req.moduleDir(), req.edits());
            if (applyError.isPresent()) {
                return done(Verdict.ESCALATE, "patch rejected before build: " + applyError.get(),
                        baseline, null, List.of(), start);
            }

            BuildOutcome candidate = build(checkout, req);
            List<TestDelta> delta = TestDeltas.compute(baseline.tests(), candidate.tests());
            return decide(baseline, candidate, delta, start);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return runnerError(e, start);
        } finally {
            cleanup(repoDir, worktree);
            buildPermits.release();
        }
    }

    /**
     * Compile the module, then — if there are tests to run — run just the affected subset via
     * {@code surefire:test} against the already-compiled classes. Splitting compile from test lets
     * the verdict tell a compile failure apart from a test failure, which matters: the former is a
     * clean reject, the latter escalates.
     */
    private BuildOutcome build(Path checkout, SandboxRunRequest req) throws IOException, InterruptedException {
        Processes.Exec compile = maven.invoke(checkout, req.moduleDir(), List.of("test-compile"));
        if (compile.timedOut()) {
            return new BuildOutcome(false, true, compile.exitCode(), Map.of(), compile.output());
        }
        if (compile.exitCode() != 0) {
            return new BuildOutcome(false, false, compile.exitCode(), Map.of(), compile.output());
        }
        if (req.testClasses().isEmpty()) {
            return new BuildOutcome(true, false, 0, Map.of(), compile.output());
        }

        Path reports = reportsDir(checkout, req.moduleDir());
        deleteRecursively(reports); // avoid reading a previous phase's reports
        String selection = String.join(",", req.testClasses());
        Processes.Exec test = maven.invoke(checkout, req.moduleDir(),
                List.of("surefire:test", "-Dtest=" + selection, "-DfailIfNoTests=false"));

        // Parse regardless of exit code: a test failure is a non-zero exit but still writes reports.
        Map<String, TestStatus> results = surefire.parse(reports);
        return new BuildOutcome(true, test.timedOut(), test.exitCode(), results, test.output());
    }

    private SandboxResult decide(BuildOutcome baseline, BuildOutcome candidate,
                                 List<TestDelta> delta, long start) {
        if (!candidate.compiled()) {
            String why = candidate.timedOut()
                    ? "candidate build timed out"
                    : "patched code does not compile";
            // A timeout is infrastructure-suspicious (escalate); a plain compile failure is a clean reject.
            Verdict v = candidate.timedOut() ? Verdict.ESCALATE : Verdict.REJECTED;
            return done(v, why, baseline, candidate, delta, start);
        }
        if (!baseline.green()) {
            return done(Verdict.ESCALATE,
                    "baseline is not green; a green-to-green comparison is impossible", baseline, candidate, delta, start);
        }
        if (!delta.isEmpty()) {
            return done(Verdict.ESCALATE, "test result set changed: " + summarize(delta),
                    baseline, candidate, delta, start);
        }
        if (!candidate.green()) {
            return done(Verdict.ESCALATE, "candidate is not green despite an empty delta",
                    baseline, candidate, delta, start);
        }
        return done(Verdict.ACCEPTED, "green-to-green with an unchanged test set", baseline, candidate, delta, start);
    }

    private static String summarize(List<TestDelta> delta) {
        return delta.stream()
                .sorted(Comparator.comparing(d -> d.kind().name()))
                .map(d -> d.id() + " (" + d.kind() + ")")
                .limit(20)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private Path reportsDir(Path checkout, String moduleDir) {
        Path base = (moduleDir == null || moduleDir.isBlank()) ? checkout : checkout.resolve(moduleDir);
        return base.resolve("target").resolve("surefire-reports");
    }

    private void cleanup(Path repoDir, Path worktree) {
        if (worktree == null) {
            return;
        }
        try {
            git.remove(repoDir, worktree.resolve("repo"));
        } catch (Exception e) {
            log.warn("git worktree remove failed for {}", worktree, e);
        }
        deleteRecursively(worktree);
    }

    private SandboxResult runnerError(Exception e, long start) {
        log.error("sandbox run failed", e);
        return done(Verdict.ESCALATE, "sandbox error: " + e.getClass().getSimpleName() + ": " + e.getMessage(),
                null, null, List.of(), start);
    }

    private SandboxResult done(Verdict verdict, String reason, BuildOutcome baseline,
                               BuildOutcome candidate, List<TestDelta> delta, long start) {
        long ms = System.currentTimeMillis() - start;
        log.info("sandbox verdict={} ({}) in {}ms", verdict, reason, ms);
        return new SandboxResult(verdict, reason, baseline, candidate, delta, ms);
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
                    // Best effort; a leaked temp file is not worth failing the run over.
                }
            });
        } catch (IOException ignored) {
            // Directory already gone or unreadable; nothing more to do.
        }
    }
}
