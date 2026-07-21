package com.ai.agents.controllers;

import com.ai.agents.domain.PullRequest;
import com.ai.agents.domain.TriageVerdict;
import com.ai.agents.services.TriageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/triage")
public class TriageController {

    private final TriageService service;

    public TriageController(TriageService service) {
        this.service = service;
    }

    /**
     * Triage a pull request's changed files.
     *
     * <p>Synchronous — a single model round-trip, so expect a second or two. Returns one
     * verdict per changed file.
     *
     * <pre>{@code
     * curl -X POST localhost:8080/api/triage \
     *   -H 'Content-Type: application/json' \
     *   -d '{"repo":"acme/billing","baseRef":"main","prTitle":"Cache invoice totals",
     *        "prBody":"Adds a memoized cache","changedFiles":[
     *          {"path":"src/main/java/com/acme/InvoiceCache.java",
     *           "diff":"@@ -1,3 +1,7 @@\n+public class InvoiceCache { ... }"}]}'
     * }</pre>
     */
    @PostMapping
    public ResponseEntity<List<TriageVerdict>> triage(@Valid @RequestBody PullRequest pullRequest) {
        return ResponseEntity.ok(service.triage(pullRequest));
    }
}
