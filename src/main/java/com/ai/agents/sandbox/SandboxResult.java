package com.ai.agents.sandbox;

import java.util.List;
import java.util.Map;

/**
 * The verdict of one sandbox run: whether the patch is safe to keep, plus the evidence.
 *
 * <p>{@link Verdict#ACCEPTED} means green-to-green with an unchanged test set — safe to commit or
 * suggest. {@link Verdict#REJECTED} means the patched code does not compile — discard, no human
 * needed. {@link Verdict#ESCALATE} means anything suspicious: the baseline was not green, the test
 * set changed in either direction, a build timed out, or the runner itself failed. Both directions
 * of test change escalate on purpose — a failing test turned green is as suspect as a passing test
 * broken.
 */
public record SandboxResult(
        Verdict verdict,
        String reason,
        BuildOutcome baseline,
        BuildOutcome candidate,
        List<TestDelta> testDelta,
        long durationMs) {

    public SandboxResult {
        testDelta = testDelta == null ? List.of() : List.copyOf(testDelta);
    }

    public enum Verdict {ACCEPTED, REJECTED, ESCALATE}

    public enum TestStatus {PASSED, FAILED, ERROR, SKIPPED}

    /** Whether a status counts as a failure. SKIPPED and PASSED do not. */
    public static boolean isFailure(TestStatus s) {
        return s == TestStatus.FAILED || s == TestStatus.ERROR;
    }

    /**
     * The result of building (and optionally testing) one revision.
     *
     * @param compiled   true if {@code test-compile} succeeded.
     * @param timedOut   true if the build was killed at the wall-clock limit.
     * @param exitCode   the Maven exit code of the last phase run.
     * @param tests      per-test status, keyed by {@code classname#method}. Empty in compile-only runs.
     * @param outputTail the tail of the Maven output, for diagnosis.
     */
    public record BuildOutcome(
            boolean compiled,
            boolean timedOut,
            int exitCode,
            Map<String, TestStatus> tests,
            String outputTail) {

        public BuildOutcome {
            tests = tests == null ? Map.of() : Map.copyOf(tests);
        }

        /** Green = compiled, not timed out, and no test failed. */
        public boolean green() {
            return compiled && !timedOut && tests.values().stream().noneMatch(SandboxResult::isFailure);
        }
    }

    public enum DeltaKind {NOW_FAILING, NOW_PASSING, NEWLY_RUN, NO_LONGER_RUN, STATUS_CHANGED}

    /**
     * A single test whose outcome differs between baseline and candidate.
     *
     * @param id     the test identifier, {@code classname#method}.
     * @param kind   how it changed.
     * @param before status before the patch, or {@code null} if it did not run.
     * @param after  status after the patch, or {@code null} if it did not run.
     */
    public record TestDelta(String id, DeltaKind kind, TestStatus before, TestStatus after) {
    }
}
