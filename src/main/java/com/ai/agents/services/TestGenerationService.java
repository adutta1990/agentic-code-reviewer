package com.ai.agents.services;

import com.ai.agents.agenticService.TestAgent;
import com.ai.agents.domain.GeneratedTest;
import com.ai.agents.domain.TestGenRequest;
import com.ai.agents.domain.TestRepairRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates test authoring and repair.
 *
 * <p>Unlike the fix engine, the real validator here is the build the caller runs on the returned
 * source — that is what the repair pass exists to react to. So the only Java-side guard is a
 * consistency check: a result claiming to be testable must actually carry source; if it does not,
 * it is downgraded to an untestable report rather than handed to the caller to compile nothing.
 * On any engine failure the service reports untestable instead of 500-ing.
 */
@Service
public class TestGenerationService {

    private static final Logger log = LoggerFactory.getLogger(TestGenerationService.class);

    private final TestAgent agent;

    public TestGenerationService(TestAgent agent) {
        this.agent = agent;
    }

    public GeneratedTest generate(TestGenRequest request) {
        return guard(() -> agent.generate(request));
    }

    public GeneratedTest repair(TestRepairRequest request) {
        return guard(() -> agent.repair(request));
    }

    private GeneratedTest guard(java.util.function.Supplier<GeneratedTest> call) {
        try {
            GeneratedTest result = call.get();
            if (result.testable() && result.source().isBlank()) {
                log.warn("Downgrading to untestable: marked testable but returned empty source");
                return GeneratedTest.untestable("The author marked the class testable but returned no source.");
            }
            return result;
        } catch (Exception e) {
            log.error("Test author failed; reporting untestable", e);
            return GeneratedTest.untestable("Test author failed: " + e.getClass().getSimpleName());
        }
    }
}
