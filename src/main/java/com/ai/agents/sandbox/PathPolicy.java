package com.ai.agents.sandbox;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * The path allowlist for patch edits. Pure, per-module, unit-testable in isolation.
 *
 * <p>A single rule does the work: an edit must be a relative path under {@code <module>/src/}.
 * That one constraint forbids everything the guardrails require — {@code pom.xml} and other build
 * files (repo root, not under {@code src}), CI config ({@code .github/}, {@code .mvn/}, not under
 * {@code src}), and any file outside the module under review (a different module's {@code src}).
 * Absolute paths and {@code ..} traversal are rejected outright.
 */
public final class PathPolicy {

    private final String requiredPrefix;

    /** @param moduleDir module under review, repo-relative; {@code ""} or null means repo root. */
    public PathPolicy(String moduleDir) {
        String m = moduleDir == null ? "" : moduleDir.strip().replace('\\', '/');
        while (m.endsWith("/")) {
            m = m.substring(0, m.length() - 1);
        }
        this.requiredPrefix = m.isEmpty() ? "src/" : m + "/src/";
    }

    /** The prefix every allowed path must start with, e.g. {@code "src/"} or {@code "billing/src/"}. */
    public String requiredPrefix() {
        return requiredPrefix;
    }

    /**
     * @return the reason the path is disallowed, or empty if the edit may proceed.
     */
    public Optional<String> rejectionReason(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return Optional.of("empty path");
        }
        String norm = rawPath.strip().replace('\\', '/');
        if (norm.startsWith("/") || (norm.length() > 1 && norm.charAt(1) == ':')) {
            return Optional.of("absolute path not allowed: " + rawPath);
        }

        Path normalized = Paths.get(norm).normalize();
        String clean = normalized.toString().replace('\\', '/');
        if (clean.equals("..") || clean.startsWith("../")) {
            return Optional.of("path escapes the repository: " + rawPath);
        }
        if (!clean.startsWith(requiredPrefix)) {
            return Optional.of("outside the module under review: " + rawPath
                    + " (edits must be under " + requiredPrefix + ")");
        }
        return Optional.empty();
    }
}
