package com.ai.agents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code github.*}. When {@link #token} is blank the suggestion sink renders the block but
 * does not post it, so a CI caller can post it instead — the driver never needs a token to run.
 */
@ConfigurationProperties(prefix = "github")
public class GitHubProperties {

    private String apiUrl = "https://api.github.com";

    /** Token with pull-request write scope. Blank disables posting (render-only). */
    private String token = "";

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }
}
