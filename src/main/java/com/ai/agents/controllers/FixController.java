package com.ai.agents.controllers;

import com.ai.agents.domain.FixProposal;
import com.ai.agents.domain.FixRequest;
import com.ai.agents.services.FixService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fix")
public class FixController {

    private final FixService service;

    public FixController(FixService service) {
        this.service = service;
    }

    /**
     * Propose a mechanical fix for one finding within a line range.
     *
     * <p>Returns a replacement for the target lines when the fix is safe to apply, or a refusal
     * ({@code applicable=false}) with a reason when it is not. The allowed-imports whitelist is
     * enforced in Java, so a proposal that introduces a disallowed import comes back as a refusal.
     *
     * <pre>{@code
     * curl -X POST localhost:8080/api/fix \
     *   -H 'Content-Type: application/json' \
     *   -d '{"path":"src/main/java/com/acme/InvoiceCache.java",
     *        "findingTitle":"Unbounded cache grows without eviction",
     *        "findingExplanation":"The HashMap is never trimmed; under load it leaks memory.",
     *        "startLine":12,"endLine":12,
     *        "context":"    12    private final Map<String,Total> cache = new HashMap<>();",
     *        "existingImports":["java.util.Map","java.util.HashMap"],
     *        "allowedImports":["java.util.Map","java.util.LinkedHashMap"],
     *        "indentStyle":"4 spaces"}'
     * }</pre>
     */
    @PostMapping
    public ResponseEntity<FixProposal> fix(@Valid @RequestBody FixRequest request) {
        return ResponseEntity.ok(service.propose(request));
    }
}
