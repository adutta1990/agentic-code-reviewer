package com.ai.agents.agenticService;

import com.ai.agents.config.ReviewProperties;
import com.ai.agents.domain.GeneratedTest;
import com.ai.agents.domain.TestGenRequest;
import com.ai.agents.domain.TestRepairRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * The JUnit 5 test author.
 *
 * <p>Two modes over one system prompt: {@link #generate} writes fresh tests for a changed class;
 * {@link #repair} takes a test that failed to compile or run, plus the build output, and fixes it
 * without weakening assertions. The system prompt is constant — only the user message differs —
 * so both modes share a single {@code defaultSystem}. The assertion library is injected from
 * config, since a repo picks one assertion style rather than switching it per PR.
 */
@Service
public class TestAgent {

    private static final Logger log = LoggerFactory.getLogger(TestAgent.class);

    private final ChatClient chatClient;

    public TestAgent(ChatClient.Builder builder, ReviewProperties props) {
        this.chatClient = builder
                .defaultSystem(systemPrompt(props))
                .build();
    }

    /** Author tests for a changed class. One model round-trip. Never returns null. */
    public GeneratedTest generate(TestGenRequest request) {
        log.info("Generating tests: changedMethods={}", request.changedMethods());
        return call(request.toUserPrompt());
    }

    /** Repair a test that failed its build, using the build output. One round-trip. Never null. */
    public GeneratedTest repair(TestRepairRequest request) {
        log.info("Repairing tests after failed build");
        return call(request.original().toRepairPrompt(request.failedTestSource(), request.buildOutput()));
    }

    private GeneratedTest call(String userMessage) {
        GeneratedTest result = chatClient.prompt()
                .user(userMessage)
                .call()
                .entity(GeneratedTest.class);

        if (result == null) {
            return GeneratedTest.untestable("The test author returned no result.");
        }
        log.info("Test author: testable={} covers={}", result.testable(), result.covers().size());
        return result;
    }

    /**
     * The test-author system prompt. The assertion library is injected; the rest — one test per
     * behavior, coverage ordering, the bans on log/timing/toString assertions and Thread.sleep,
     * and the instruction to report untestability rather than write a weak test — is constant.
     */
    private static String systemPrompt(ReviewProperties props) {
        return """
               You are a Java test author. You write JUnit 5 tests that fail when the code is
               wrong and pass when it is right. A test that passes against a deliberately
               broken implementation is a defect you introduced.

               RULES
               - One @Test per behavior, named should<Behavior>When<Condition>.
               - Use %s assertions. Use Mockito only for collaborators that
                 perform I/O; never mock the class under test, never mock value objects.
               - Cover, in this order: the happy path, each documented exception path, each
                 boundary in the changed logic (empty, null, zero, max, off-by-one edges).
               - Never write assertions on log output, timing, or toString().
               - Never use Thread.sleep. Never write a test whose assertion is assertNotNull
                 alone.
               - If a method is untestable without changing production code, say so instead of
                 writing a weak test.

               Return a complete, compilable JUnit 5 test file. If the class cannot be tested
               without changing production code, set testable=false and put the reason in
               blocker. List each behavior you covered in `covers`.
               """.formatted(props.getAssertionLib());
    }
}
