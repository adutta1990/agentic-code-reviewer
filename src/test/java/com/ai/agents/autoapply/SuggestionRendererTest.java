package com.ai.agents.autoapply;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggestionRendererTest {

    @Test
    void shouldWrapReplacementInSuggestionFenceWithTitle() {
        String body = SuggestionRenderer.render("Unbounded cache", "    int y = 0;");
        assertEquals("**Unbounded cache**\n\n```suggestion\n    int y = 0;\n```", body);
    }

    @Test
    void shouldOmitTitleHeaderWhenTitleBlank() {
        String body = SuggestionRenderer.render("  ", "code();");
        assertEquals("```suggestion\ncode();\n```", body);
    }

    @Test
    void shouldNotDoubleTheTrailingNewlineBeforeTheClosingFence() {
        String body = SuggestionRenderer.render(null, "code();\n");
        assertTrue(body.endsWith("code();\n```"));
        assertFalse(body.contains("code();\n\n```"));
    }
}
