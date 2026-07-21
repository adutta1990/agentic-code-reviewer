package com.ai.agents.controllers;

import com.ai.agents.domain.PullRequestReviewRequest;
import com.ai.agents.domain.PullRequestReviewResult;
import com.ai.agents.services.PullRequestReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pull-requests")
public class PullRequestReviewController {

    private final PullRequestReviewService service;

    public PullRequestReviewController(PullRequestReviewService service) {
        this.service = service;
    }

    /**
     * Run the full triage → review pipeline over a pull request.
     *
     * <p>Triage gates every changed file; only the flagged files are sent to the line-level
     * reviewer, which runs in bounded parallel. The response reports every file's triage verdict
     * plus the findings for the files that were reviewed.
     *
     * <pre>{@code
     * curl -X POST localhost:8080/api/pull-requests/review \
     *   -H 'Content-Type: application/json' \
     *   -d '{"repo":"acme/billing","baseRef":"main","prTitle":"Cache invoice totals",
     *        "prBody":"Adds a memoized cache","javaVersion":"17","frameworks":["Spring Boot"],
     *        "conventions":"Prefer constructor injection.","files":[
     *          {"path":"src/main/java/com/acme/InvoiceCache.java",
     *           "diff":"@@ -1,3 +1,7 @@\n+public class InvoiceCache { ... }",
     *           "fileContent":"package com.acme;\n...",
     *           "changedRanges":[{"start":10,"end":15}]}]}'
     * }</pre>
     */
    @PostMapping("/review")
    public ResponseEntity<PullRequestReviewResult> review(@Valid @RequestBody PullRequestReviewRequest request) {
        return ResponseEntity.ok(service.review(request));
    }
}
