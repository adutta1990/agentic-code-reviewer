package com.ai.agents.autoapply;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PatchBuilderTest {

    @Test
    void shouldReplaceSingleLineInMiddleWhenRangeIsOneLine() {
        String out = PatchBuilder.splice("a\nb\nc\n", 2, 2, "B");
        assertEquals("a\nB\nc\n", out);
    }

    @Test
    void shouldReplaceMultiLineRangeWithMultiLineReplacement() {
        String out = PatchBuilder.splice("a\nb\nc\nd\n", 2, 3, "X\nY");
        assertEquals("a\nX\nY\nd\n", out);
    }

    @Test
    void shouldReplaceFirstLineWhenRangeStartsAtOne() {
        assertEquals("A\nb\nc\n", PatchBuilder.splice("a\nb\nc\n", 1, 1, "A"));
    }

    @Test
    void shouldPreserveAbsenceOfTrailingNewlineWhenReplacingLastLine() {
        // Original has no trailing newline; output must not gain one.
        assertEquals("a\nb\nC", PatchBuilder.splice("a\nb\nc", 3, 3, "C"));
    }

    @Test
    void shouldPreserveIndentationCarriedInTheReplacement() {
        String out = PatchBuilder.splice("class X {\n  int y;\n}\n", 2, 2, "    int y = 0;");
        assertEquals("class X {\n    int y = 0;\n}\n", out);
    }

    @Test
    void shouldPreserveCrlfSeparatorsWhenFileUsesThem() {
        String out = PatchBuilder.splice("a\r\nb\r\nc\r\n", 2, 2, "B");
        assertEquals("a\r\nB\r\nc\r\n", out);
    }

    @Test
    void shouldNormalizeReplacementLineEndingsToTheFileSeparator() {
        // Replacement uses LF but the file uses CRLF; output must be uniform CRLF.
        String out = PatchBuilder.splice("a\r\nb\r\nc\r\n", 2, 2, "X\nY");
        assertEquals("a\r\nX\r\nY\r\nc\r\n", out);
    }

    @Test
    void shouldDeleteLinesWhenReplacementIsEmpty() {
        assertEquals("a\nc\n", PatchBuilder.splice("a\nb\nc\n", 2, 2, ""));
    }

    @Test
    void shouldRejectRangeBeyondEndOfFile() {
        assertThrows(IllegalArgumentException.class, () -> PatchBuilder.splice("a\nb\n", 2, 5, "x"));
    }

    @Test
    void shouldRejectInvertedRange() {
        assertThrows(IllegalArgumentException.class, () -> PatchBuilder.splice("a\nb\nc\n", 3, 2, "x"));
    }
}
