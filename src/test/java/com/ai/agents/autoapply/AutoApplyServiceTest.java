package com.ai.agents.autoapply;

import com.ai.agents.config.AutoApplyProperties;
import com.ai.agents.domain.FixProposal;
import com.ai.agents.domain.FixProposal.BehaviorChange;
import com.ai.agents.sandbox.SandboxResult;
import com.ai.agents.sandbox.SandboxResult.Verdict;
import com.ai.agents.sandbox.SandboxRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoApplyServiceTest {

    private SandboxRunner sandbox;
    private GitFileReader fileReader;
    private GitHubSuggestionSink suggestionSink;
    private BranchCommitSink commitSink;
    private AutoApplyService service;

    @BeforeEach
    void setUp() throws Exception {
        sandbox = mock(SandboxRunner.class);
        fileReader = mock(GitFileReader.class);
        suggestionSink = mock(GitHubSuggestionSink.class);
        commitSink = mock(BranchCommitSink.class);
        service = new AutoApplyService(sandbox, fileReader, suggestionSink, commitSink, new AutoApplyProperties());

        when(fileReader.read(any(), any(), any())).thenReturn("line1\nline2\nline3\n");
    }

    @Test
    void shouldGateWhenProposalNotApplicable() {
        VerifiedFixResult result = service.apply(request(notApplicable(), AutoApplyMode.SUGGEST));

        assertEquals(VerifiedFixResult.Status.GATED_NOT_APPLICABLE, result.status());
        verify(sandbox, never()).run(any());
    }

    @Test
    void shouldGateBehaviorChangeWhenAutofixOnlyAndNotNone() {
        VerifiedFixResult result = service.apply(request(applicable(BehaviorChange.SEMANTIC), AutoApplyMode.AUTOFIX_ONLY));

        assertEquals(VerifiedFixResult.Status.GATED_BEHAVIOR_CHANGE, result.status());
        verify(sandbox, never()).run(any());
    }

    @Test
    void shouldPostSuggestionWhenAcceptedInSuggestMode() {
        when(sandbox.run(any())).thenReturn(verdict(Verdict.ACCEPTED));
        when(suggestionSink.publish(any())).thenReturn(SinkResult.published("posted", "http://x"));

        VerifiedFixResult result = service.apply(request(applicable(BehaviorChange.NONE), AutoApplyMode.SUGGEST));

        assertEquals(VerifiedFixResult.Status.PUBLISHED_SUGGESTION, result.status());
        verify(suggestionSink).publish(any());
        verify(commitSink, never()).publish(any());
    }

    @Test
    void shouldCommitWhenAcceptedInCommitMode() {
        when(sandbox.run(any())).thenReturn(verdict(Verdict.ACCEPTED));
        when(commitSink.publish(any())).thenReturn(SinkResult.published("pushed", "abc123"));

        VerifiedFixResult result = service.apply(request(applicable(BehaviorChange.NONE), AutoApplyMode.COMMIT));

        assertEquals(VerifiedFixResult.Status.COMMITTED, result.status());
        verify(commitSink).publish(any());
        verify(suggestionSink, never()).publish(any());
    }

    @Test
    void shouldNotPublishWhenBuildRejected() {
        when(sandbox.run(any())).thenReturn(verdict(Verdict.REJECTED));

        VerifiedFixResult result = service.apply(request(applicable(BehaviorChange.NONE), AutoApplyMode.SUGGEST));

        assertEquals(VerifiedFixResult.Status.REJECTED_BUILD, result.status());
        verify(suggestionSink, never()).publish(any());
    }

    @Test
    void shouldEscalateWhenVerdictEscalate() {
        when(sandbox.run(any())).thenReturn(verdict(Verdict.ESCALATE));

        VerifiedFixResult result = service.apply(request(applicable(BehaviorChange.NONE), AutoApplyMode.SUGGEST));

        assertEquals(VerifiedFixResult.Status.ESCALATED, result.status());
        verify(commitSink, never()).publish(any());
    }

    @Test
    void shouldStillRenderSuggestionWhenSinkSkips() {
        when(sandbox.run(any())).thenReturn(verdict(Verdict.ACCEPTED));
        when(suggestionSink.publish(any())).thenReturn(SinkResult.skipped("no token"));

        VerifiedFixResult result = service.apply(request(applicable(BehaviorChange.NONE), AutoApplyMode.SUGGEST));

        assertEquals(VerifiedFixResult.Status.SINK_SKIPPED, result.status());
        // The rendered block is still returned so a caller can post it.
        org.junit.jupiter.api.Assertions.assertTrue(result.suggestion().contains("```suggestion"));
    }

    // --- fixtures ---

    private static VerifiedFixRequest request(FixProposal proposal, AutoApplyMode mode) {
        return new VerifiedFixRequest(
                "/repo", "sha123", "", "src/main/java/com/acme/Foo.java", 2, 2,
                "A finding", proposal, List.of("com.acme.FooTest"), mode, null);
    }

    private static FixProposal applicable(BehaviorChange change) {
        return new FixProposal(true, null, List.of(), "    replaced();", change, null);
    }

    private static FixProposal notApplicable() {
        return new FixProposal(false, "cannot fix in range", List.of(), "", BehaviorChange.NONE, null);
    }

    private static SandboxResult verdict(Verdict v) {
        return new SandboxResult(v, v.name(), null, null, List.of(), 10);
    }
}
