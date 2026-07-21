package com.ai.agents.controllers;

import com.ai.agents.autoapply.AutoApplyService;
import com.ai.agents.autoapply.VerifiedFixRequest;
import com.ai.agents.autoapply.VerifiedFixResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auto-apply")
public class AutoApplyController {

    private final AutoApplyService service;

    public AutoApplyController(AutoApplyService service) {
        this.service = service;
    }

    /**
     * Verify a fix and route it to the configured sink.
     *
     * <p>By default ({@code SUGGEST} mode, push disabled, no GitHub token) this verifies the patch
     * in the sandbox and returns the rendered suggestion without mutating anything external. Enable
     * a token to post suggestions, or {@code COMMIT}/{@code AUTOFIX_ONLY} with
     * {@code allow-push=true} to push verified fixes.
     *
     * <pre>{@code
     * curl -X POST localhost:8080/api/auto-apply \
     *   -H 'Content-Type: application/json' \
     *   -d '{"repoDir":"/path/to/warm/checkout","sha":"<head-sha>","moduleDir":"",
     *        "filePath":"src/main/java/com/acme/InvoiceCache.java","startLine":12,"endLine":12,
     *        "findingTitle":"Unbounded cache grows without eviction",
     *        "fixProposal":{"applicable":true,"reasonIfNot":null,"newImports":[],
     *          "replacement":"    private final Map<String,Total> cache = new LinkedHashMap<>(){...};",
     *          "behaviorChange":"NONE","riskNote":null},
     *        "testClasses":["com.acme.InvoiceCacheTest"],"mode":"SUGGEST",
     *        "pr":{"owner":"acme","repo":"billing","number":42,"branch":"feature/cache"}}'
     * }</pre>
     */
    @PostMapping
    public ResponseEntity<VerifiedFixResult> apply(@Valid @RequestBody VerifiedFixRequest request) {
        return ResponseEntity.ok(service.apply(request));
    }
}
