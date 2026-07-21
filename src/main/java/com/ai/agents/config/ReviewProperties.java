package com.ai.agents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds the {@code code-review.*} block in application.yaml.
 *
 * <p>These fill the reviewer's system-prompt placeholders and enforce its confidence floor.
 * {@code styleGuide} and {@code existingLinters} keep the reviewer from re-flagging what a
 * formatter or an existing linter already catches; {@code minConfidence} is the hard floor
 * below which a finding is dropped even if the model returned it.
 */
@ConfigurationProperties(prefix = "code-review")
public class ReviewProperties {

    /** Name of the style guide the reviewer may cite for style findings. */
    private String styleGuide = "the project style guide";

    /** Linters already running in CI; the reviewer must not duplicate their findings. */
    private List<String> existingLinters = List.of("Checkstyle", "SpotBugs");

    /** Assertion library the test author writes against, e.g. {@code "AssertJ"} or {@code "JUnit 5"}. */
    private String assertionLib = "AssertJ";

    /** Findings below this confidence are suppressed. The prompt states 0.6; this enforces it. */
    private double minConfidence = 0.6;

    /**
     * Upper bound on files reviewed in parallel by the PR orchestrator. Bounds fan-out so a
     * large PR cannot open one model call per file at once and trip provider rate limits.
     */
    private int maxConcurrentReviews = 4;

    public String getStyleGuide() {
        return styleGuide;
    }

    public void setStyleGuide(String styleGuide) {
        this.styleGuide = styleGuide;
    }

    public List<String> getExistingLinters() {
        return existingLinters;
    }

    public void setExistingLinters(List<String> existingLinters) {
        this.existingLinters = existingLinters == null ? List.of() : List.copyOf(existingLinters);
    }

    public String getAssertionLib() {
        return assertionLib;
    }

    public void setAssertionLib(String assertionLib) {
        this.assertionLib = assertionLib;
    }

    public double getMinConfidence() {
        return minConfidence;
    }

    public void setMinConfidence(double minConfidence) {
        this.minConfidence = minConfidence;
    }

    public int getMaxConcurrentReviews() {
        return maxConcurrentReviews;
    }

    public void setMaxConcurrentReviews(int maxConcurrentReviews) {
        this.maxConcurrentReviews = maxConcurrentReviews;
    }

    /** Human-readable linter list for the prompt, e.g. {@code "Checkstyle, SpotBugs"}. */
    public String existingLintersText() {
        return existingLinters.isEmpty() ? "the configured linters" : String.join(", ", existingLinters);
    }
}
