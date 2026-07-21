package com.ai.agents;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full-context boot test.
 *
 * <p>Booting the whole application needs a real OPENAI_API_KEY — the OpenAI auto-configuration
 * fails fast without one. So this is gated on the key being present rather than left to fail for
 * everyone without it.
 *
 * <p>To run it:
 * <pre>
 *   export OPENAI_API_KEY=sk-...
 *   ./mvnw test
 * </pre>
 *
 * <p>The logic that is worth testing without a live model — the sandbox runner's path allowlist,
 * test-delta comparison, and report parsing — is covered by the {@code com.ai.agents.sandbox}
 * tests, which need no key.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+",
        disabledReason = "needs a real OPENAI_API_KEY")
class AgenticCodeReviewerApplicationTests {

    @Test
    void contextLoads() {
    }
}
