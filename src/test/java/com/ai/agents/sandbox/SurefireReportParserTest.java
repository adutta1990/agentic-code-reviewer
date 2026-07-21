package com.ai.agents.sandbox;

import com.ai.agents.sandbox.SandboxResult.TestStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurefireReportParserTest {

    private final SurefireReportParser parser = new SurefireReportParser();

    @Test
    void shouldMapEachTestcaseToItsStatusWhenReportHasMixedResults(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("TEST-com.acme.FooTest.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="com.acme.FooTest" tests="4">
                  <testcase name="passes" classname="com.acme.FooTest"/>
                  <testcase name="fails" classname="com.acme.FooTest">
                    <failure message="expected true"/>
                  </testcase>
                  <testcase name="errors" classname="com.acme.FooTest">
                    <error message="NPE"/>
                  </testcase>
                  <testcase name="skips" classname="com.acme.FooTest">
                    <skipped/>
                  </testcase>
                </testsuite>
                """);

        Map<String, TestStatus> results = parser.parse(dir);

        assertEquals(TestStatus.PASSED, results.get("com.acme.FooTest#passes"));
        assertEquals(TestStatus.FAILED, results.get("com.acme.FooTest#fails"));
        assertEquals(TestStatus.ERROR, results.get("com.acme.FooTest#errors"));
        assertEquals(TestStatus.SKIPPED, results.get("com.acme.FooTest#skips"));
    }

    @Test
    void shouldReturnEmptyWhenNoReportsDirectory(@TempDir Path dir) throws IOException {
        assertTrue(parser.parse(dir.resolve("does-not-exist")).isEmpty());
    }

    @Test
    void shouldIgnoreNonReportFilesWhenDirectoryHasOtherFiles(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("foo.txt"), "not a report");
        Files.writeString(dir.resolve("com.acme.FooTest.txt"), "surefire text output, not xml");

        assertTrue(parser.parse(dir).isEmpty());
    }
}
