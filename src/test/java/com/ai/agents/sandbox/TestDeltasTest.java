package com.ai.agents.sandbox;

import com.ai.agents.sandbox.SandboxResult.DeltaKind;
import com.ai.agents.sandbox.SandboxResult.TestDelta;
import com.ai.agents.sandbox.SandboxResult.TestStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDeltasTest {

    @Test
    void shouldReportNoDeltaWhenResultsIdentical() {
        var before = Map.of("A#a", TestStatus.PASSED, "A#b", TestStatus.PASSED);
        assertTrue(TestDeltas.compute(before, before).isEmpty());
    }

    @Test
    void shouldReportNowFailingWhenPassingTestBreaks() {
        var before = Map.of("A#a", TestStatus.PASSED);
        var after = Map.of("A#a", TestStatus.FAILED);

        List<TestDelta> delta = TestDeltas.compute(before, after);

        assertEquals(1, delta.size());
        assertEquals(DeltaKind.NOW_FAILING, delta.get(0).kind());
    }

    @Test
    void shouldReportNowPassingWhenFailingTestFlipsGreen() {
        // The suspicious case: a patch that turns a red test green must still surface, not hide.
        var before = Map.of("A#a", TestStatus.FAILED);
        var after = Map.of("A#a", TestStatus.PASSED);

        List<TestDelta> delta = TestDeltas.compute(before, after);

        assertEquals(1, delta.size());
        assertEquals(DeltaKind.NOW_PASSING, delta.get(0).kind());
    }

    @Test
    void shouldReportNoLongerRunWhenTestDisappears() {
        var before = Map.of("A#a", TestStatus.PASSED);
        var after = Map.<String, TestStatus>of();

        List<TestDelta> delta = TestDeltas.compute(before, after);

        assertEquals(1, delta.size());
        assertEquals(DeltaKind.NO_LONGER_RUN, delta.get(0).kind());
    }

    @Test
    void shouldReportNewlyRunWhenTestAppears() {
        var before = Map.<String, TestStatus>of();
        var after = Map.of("A#a", TestStatus.PASSED);

        List<TestDelta> delta = TestDeltas.compute(before, after);

        assertEquals(1, delta.size());
        assertEquals(DeltaKind.NEWLY_RUN, delta.get(0).kind());
    }

    @Test
    void shouldReportStatusChangedWhenPassedBecomesSkipped() {
        var before = Map.of("A#a", TestStatus.PASSED);
        var after = Map.of("A#a", TestStatus.SKIPPED);

        List<TestDelta> delta = TestDeltas.compute(before, after);

        assertEquals(1, delta.size());
        assertEquals(DeltaKind.STATUS_CHANGED, delta.get(0).kind());
    }
}
