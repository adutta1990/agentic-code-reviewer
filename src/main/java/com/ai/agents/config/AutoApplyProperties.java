package com.ai.agents.config;

import com.ai.agents.autoapply.AutoApplyMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code code-review.auto-apply.*}. The defaults are deliberately inert: {@code SUGGEST}
 * mode and {@code allow-push=false} mean the driver verifies and renders but never mutates a
 * branch until an operator opts in.
 */
@ConfigurationProperties(prefix = "code-review.auto-apply")
public class AutoApplyProperties {

    /** Default sink when a request does not specify one. */
    private AutoApplyMode mode = AutoApplyMode.SUGGEST;

    /** Master switch for pushing commits. False means COMMIT/AUTOFIX_ONLY render but never push. */
    private boolean allowPush = false;

    private String commitAuthorName = "code-reviewer-bot";
    private String commitAuthorEmail = "code-reviewer-bot@users.noreply.github.com";
    private String commitMessagePrefix = "Auto-fix: ";

    public AutoApplyMode getMode() {
        return mode;
    }

    public void setMode(AutoApplyMode mode) {
        this.mode = mode;
    }

    public boolean isAllowPush() {
        return allowPush;
    }

    public void setAllowPush(boolean allowPush) {
        this.allowPush = allowPush;
    }

    public String getCommitAuthorName() {
        return commitAuthorName;
    }

    public void setCommitAuthorName(String commitAuthorName) {
        this.commitAuthorName = commitAuthorName;
    }

    public String getCommitAuthorEmail() {
        return commitAuthorEmail;
    }

    public void setCommitAuthorEmail(String commitAuthorEmail) {
        this.commitAuthorEmail = commitAuthorEmail;
    }

    public String getCommitMessagePrefix() {
        return commitMessagePrefix;
    }

    public void setCommitMessagePrefix(String commitMessagePrefix) {
        this.commitMessagePrefix = commitMessagePrefix;
    }
}
