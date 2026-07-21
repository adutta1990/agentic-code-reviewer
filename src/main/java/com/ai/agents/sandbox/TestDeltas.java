package com.ai.agents.sandbox;

import com.ai.agents.sandbox.SandboxResult.DeltaKind;
import com.ai.agents.sandbox.SandboxResult.TestDelta;
import com.ai.agents.sandbox.SandboxResult.TestStatus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure comparison of two test-result maps. No I/O, no framework — the piece the acceptance
 * decision rests on, isolated so it can be unit-tested directly.
 */
public final class TestDeltas {

    private TestDeltas() {
    }

    /**
     * Every test whose outcome changed between {@code before} and {@code after}. A test present in
     * only one map is a run/no-run change; a status flip is classified by whether it crossed the
     * pass/fail line, in which direction.
     */
    public static List<TestDelta> compute(Map<String, TestStatus> before, Map<String, TestStatus> after) {
        Set<String> ids = new LinkedHashSet<>();
        ids.addAll(before.keySet());
        ids.addAll(after.keySet());

        List<TestDelta> deltas = new ArrayList<>();
        for (String id : ids) {
            TestStatus b = before.get(id);
            TestStatus a = after.get(id);

            if (b == null && a != null) {
                deltas.add(new TestDelta(id, DeltaKind.NEWLY_RUN, null, a));
            } else if (b != null && a == null) {
                deltas.add(new TestDelta(id, DeltaKind.NO_LONGER_RUN, b, null));
            } else if (b != a) {
                deltas.add(new TestDelta(id, classify(b, a), b, a));
            }
        }
        return deltas;
    }

    private static DeltaKind classify(TestStatus before, TestStatus after) {
        boolean wasFail = SandboxResult.isFailure(before);
        boolean nowFail = SandboxResult.isFailure(after);
        if (wasFail && !nowFail) {
            return DeltaKind.NOW_PASSING;
        }
        if (!wasFail && nowFail) {
            return DeltaKind.NOW_FAILING;
        }
        // Same side of the pass/fail line but a different status, e.g. FAILED<->ERROR or
        // PASSED<->SKIPPED (a test that silently stopped running is still worth surfacing).
        return DeltaKind.STATUS_CHANGED;
    }
}
