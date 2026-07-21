package com.ai.agents;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full-context boot test.
 *
 * <p>Booting the whole application needs live pgvector (localhost:5432) and a real
 * OPENAI_API_KEY — the OpenAI auto-configuration fails fast without a key, and the ingestion
 * runner talks to the database on startup. So this is gated on the key being present rather
 * than left to fail for everyone without one.
 *
 * <p>To run it:
 * <pre>
 *   docker compose up -d
 *   export OPENAI_API_KEY=sk-...
 *   ./mvnw test
 * </pre>
 *
 * <p>The logic worth testing without infrastructure — the remediation gate — is covered by
 * {@code RemediationGateTest}, which needs neither.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+",
        disabledReason = "needs a real OPENAI_API_KEY and pgvector on localhost:5432")
class AiAgentsApplicationTests {

    @Test
    void contextLoads() {
    }
}
