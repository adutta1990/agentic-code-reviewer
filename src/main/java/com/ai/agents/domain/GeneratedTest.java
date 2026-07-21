package com.ai.agents.domain;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * A generated JUnit 5 test file, or a report that the class cannot be tested as written.
 *
 * <p>Spring AI derives a JSON schema from this record and binds the model's response into it
 * via {@code .entity()}. The {@link JsonPropertyDescription} annotations are serialized into
 * that schema and become the per-field instructions the model follows. The {@link JsonProperty}
 * names pin the wire format to the snake_case schema the system prompt specifies — including
 * {@code package}, which cannot be a Java field name.
 *
 * <p>When {@link #testable()} is false the class could not be tested without changing production
 * code: {@link #source()} is empty and {@link #blocker()} explains what stands in the way. That
 * is a valid, useful answer — better than a weak test that passes against broken code.
 */
@JsonClassDescription("A complete JUnit 5 test file for a changed class, or a report that it cannot be tested as written.")
public record GeneratedTest(

        @JsonProperty("testable")
        @JsonPropertyDescription("True if you produced meaningful tests. False if the class cannot be tested without changing production code.")
        boolean testable,

        @JsonProperty("blocker")
        @JsonPropertyDescription("""
                What makes the class untestable as written, e.g. a static singleton collaborator that \
                performs I/O. Null when testable is true.""")
        String blocker,

        @JsonProperty("test_class_name")
        @JsonPropertyDescription("The generated test class's simple name, e.g. 'TriageServiceTest'.")
        String testClassName,

        @JsonProperty("package")
        @JsonPropertyDescription("The test class's package declaration, matching the class under test.")
        String packageName,

        @JsonProperty("source")
        @JsonPropertyDescription("""
                A complete, compilable JUnit 5 test file: package declaration, all imports, and the test \
                class. Empty when testable is false.""")
        String source,

        @JsonProperty("covers")
        @JsonPropertyDescription("""
                One entry per behavior tested, naming the behavior, e.g. 'flags all files worth_review on \
                triage failure'. Empty when testable is false.""")
        List<String> covers) {

    public GeneratedTest {
        covers = covers == null ? List.of() : List.copyOf(covers);
    }

    /** An untestable report: no source. Used when generation is refused or fails. */
    public static GeneratedTest untestable(String blocker) {
        return new GeneratedTest(false, blocker, "", "", "", List.of());
    }
}
