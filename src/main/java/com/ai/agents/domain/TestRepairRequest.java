package com.ai.agents.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The second pass of the test loop: a test file that failed to compile or run, handed back for
 * repair together with the build output that explains the failure.
 *
 * <p>The {@code original} request is carried through so the repair pass sees the same class,
 * changed methods, and dependencies as the generation pass — the model is stateless between
 * calls, so the context must travel with the request.
 *
 * @param original        the generation request that produced the failed test.
 * @param failedTestSource the test file that failed.
 * @param buildOutput     the tail of the Maven output showing the compile/run failure.
 */
public record TestRepairRequest(
        @NotNull @Valid TestGenRequest original,
        @NotBlank String failedTestSource,
        String buildOutput) {
}
