package com.ai.agents.controllers;

import com.ai.agents.domain.GeneratedTest;
import com.ai.agents.domain.TestGenRequest;
import com.ai.agents.domain.TestRepairRequest;
import com.ai.agents.services.TestGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tests")
public class TestGenerationController {

    private final TestGenerationService service;

    public TestGenerationController(TestGenerationService service) {
        this.service = service;
    }

    /**
     * Author JUnit 5 tests for one changed class.
     *
     * <p>Returns a complete test file, or {@code testable=false} with a blocker when the class
     * cannot be tested without changing production code.
     *
     * <pre>{@code
     * curl -X POST localhost:8080/api/tests/generate \
     *   -H 'Content-Type: application/json' \
     *   -d '{"classSource":"package com.acme;\npublic class Calc { ... }",
     *        "changedMethods":["add"],"existingTestSource":null,
     *        "testDeps":["JUnit 5","Mockito","AssertJ"]}'
     * }</pre>
     */
    @PostMapping("/generate")
    public ResponseEntity<GeneratedTest> generate(@Valid @RequestBody TestGenRequest request) {
        return ResponseEntity.ok(service.generate(request));
    }

    /**
     * Repair a generated test that failed its build.
     *
     * <p>Feed back the failed test source and the tail of the Maven output. The author fixes the
     * test without weakening assertions; if the failure reveals a real production bug, it returns
     * {@code testable=false} with the bug described in the blocker instead of forcing a pass.
     */
    @PostMapping("/repair")
    public ResponseEntity<GeneratedTest> repair(@Valid @RequestBody TestRepairRequest request) {
        return ResponseEntity.ok(service.repair(request));
    }
}
