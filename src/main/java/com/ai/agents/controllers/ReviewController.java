package com.ai.agents.controllers;

import com.ai.agents.domain.FileReviewResult;
import com.ai.agents.domain.ReviewRequest;
import com.ai.agents.services.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    /**
     * Perform a line-level review of one changed file.
     *
     * <p>Synchronous — a single model round-trip. Returns the file's path and any findings;
     * an empty findings array means the changed ranges are clean.
     *
     * <pre>{@code
     * curl -X POST localhost:8080/api/review \
     *   -H 'Content-Type: application/json' \
     *   -d '{"path":"src/main/java/com/acme/InvoiceCache.java","javaVersion":"17",
     *        "frameworks":["Spring Boot"],"conventions":"Prefer constructor injection.",
     *        "fileContent":"package com.acme;\n...","changedRanges":[{"start":10,"end":15}]}'
     * }</pre>
     */
    @PostMapping
    public ResponseEntity<FileReviewResult> review(@Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(service.review(request));
    }
}
