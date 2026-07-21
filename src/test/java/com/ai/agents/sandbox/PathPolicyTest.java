package com.ai.agents.sandbox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathPolicyTest {

    @Test
    void shouldAllowFileUnderModuleSrcWhenRepoRoot() {
        PathPolicy policy = new PathPolicy("");
        assertTrue(policy.rejectionReason("src/main/java/com/acme/Foo.java").isEmpty());
    }

    @Test
    void shouldRejectPomXmlWhenAtRepoRoot() {
        assertFalse(new PathPolicy("").rejectionReason("pom.xml").isEmpty());
    }

    @Test
    void shouldRejectCiConfigWhenOutsideSrc() {
        assertFalse(new PathPolicy("").rejectionReason(".github/workflows/build.yml").isEmpty());
    }

    @Test
    void shouldRejectParentTraversalWhenPathEscapesRepo() {
        assertFalse(new PathPolicy("").rejectionReason("../other/src/Evil.java").isEmpty());
    }

    @Test
    void shouldRejectAbsolutePathWhenGivenOne() {
        assertFalse(new PathPolicy("").rejectionReason("/etc/passwd").isEmpty());
    }

    @Test
    void shouldRejectOtherModuleWhenModuleIsScoped() {
        PathPolicy policy = new PathPolicy("billing");
        assertFalse(policy.rejectionReason("payments/src/main/java/com/acme/Bar.java").isEmpty());
    }

    @Test
    void shouldAllowScopedModuleSrcWhenModuleIsScoped() {
        PathPolicy policy = new PathPolicy("billing");
        assertTrue(policy.rejectionReason("billing/src/main/java/com/acme/Bar.java").isEmpty());
    }

    @Test
    void shouldRejectTraversalThatNormalizesBackIntoSrc() {
        // Normalizes to src/main/java/Evil.java, but only by climbing out of the repo first.
        assertFalse(new PathPolicy("").rejectionReason("src/../../repo/src/main/java/Evil.java").isEmpty());
    }
}
