package com.ai.agents.domain;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * A request to author JUnit 5 tests for one changed class.
 *
 * <p>Carries the class under test, which methods this PR changed (so the author targets the
 * changed logic rather than re-testing the world), the existing test file whose conventions to
 * extend, and the test dependencies available on the module's test classpath.
 * {@link #toUserPrompt()} renders the generation message; {@link #toRepairPrompt} renders the
 * repair message for the second pass after a failed build.
 *
 * @param classSource        the full source of the class under test.
 * @param changedMethods     methods changed in this PR; the author covers these first.
 * @param existingTestSource the existing test file to extend, or {@code null} if none exists.
 * @param testDeps           libraries on the test classpath, e.g. {@code ["JUnit 5", "Mockito", "AssertJ"]}.
 */
public record TestGenRequest(
        @NotBlank String classSource,
        List<String> changedMethods,
        String existingTestSource,
        List<String> testDeps) {

    public TestGenRequest {
        classSource = classSource == null ? "" : classSource;
        changedMethods = changedMethods == null ? List.of() : List.copyOf(changedMethods);
        testDeps = testDeps == null ? List.of() : List.copyOf(testDeps);
    }

    /** Render the generation user message in the layout the test-author system prompt expects. */
    public String toUserPrompt() {
        var sb = new StringBuilder();
        sb.append("Class under test:\n").append(classSource).append("\n\n");
        sb.append("Methods changed in this PR: ").append(String.join(", ", changedMethods)).append('\n');
        sb.append("Existing test file (extend its conventions; do not duplicate its cases):\n");
        sb.append(existingTestOrNone()).append('\n');
        sb.append("Available test dependencies: ").append(String.join(", ", testDeps));
        return sb.toString();
    }

    /**
     * Render the repair user message: the original generation context, the test that failed, and
     * the build output — plus the instruction not to weaken assertions to force a pass.
     */
    public String toRepairPrompt(String failedTestSource, String buildOutput) {
        var sb = new StringBuilder();
        sb.append(toUserPrompt()).append("\n\n");
        sb.append("Previously generated test file:\n```java\n")
                .append(failedTestSource == null ? "" : failedTestSource)
                .append("\n```\n\n");
        sb.append("""
                Your test file failed. Fix it and return the same JSON schema.
                Do not weaken assertions to make the test pass — if the test correctly
                reveals a bug in the production code, set testable=false and report it
                in `blocker` instead.

                --- BUILD OUTPUT ---
                """);
        sb.append(buildOutput == null ? "" : buildOutput);
        return sb.toString();
    }

    private String existingTestOrNone() {
        return (existingTestSource == null || existingTestSource.isBlank()) ? "NONE" : existingTestSource;
    }
}
