package com.ai.agents.controllers;

import com.ai.agents.sandbox.SandboxRunRequest;
import com.ai.agents.sandbox.SandboxResult;
import com.ai.agents.sandbox.SandboxRunner;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exercises the sandbox runner directly — no LLM involved. This is the piece that has to be right,
 * so it is callable on its own: hand it a patch and a SHA, get back green/red and the test delta.
 */
@RestController
@RequestMapping("/api/sandbox")
public class SandboxController {

    private final SandboxRunner runner;

    public SandboxController(SandboxRunner runner) {
        this.runner = runner;
    }

    /**
     * Verify a patch against a repo at a commit.
     *
     * <pre>{@code
     * curl -X POST localhost:8080/api/sandbox/run \
     *   -H 'Content-Type: application/json' \
     *   -d '{"repoDir":"/path/to/warm/checkout","sha":"<head-sha>","moduleDir":"",
     *        "edits":[{"path":"src/main/java/com/acme/Foo.java","newContent":"package com.acme; ..."}],
     *        "testClasses":["com.acme.FooTest"]}'
     * }</pre>
     */
    @PostMapping("/run")
    public ResponseEntity<SandboxResult> run(@Valid @RequestBody SandboxRunRequest request) {
        return ResponseEntity.ok(runner.run(request));
    }
}
