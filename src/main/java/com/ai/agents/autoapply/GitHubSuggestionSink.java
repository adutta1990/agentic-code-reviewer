package com.ai.agents.autoapply;

import com.ai.agents.autoapply.VerifiedFixRequest.PrContext;
import com.ai.agents.config.GitHubProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Posts a {@code ```suggestion} block as a GitHub pull-request review comment.
 *
 * <p>Render-only by default: with no token or no PR context it returns {@link SinkResult#skipped}
 * so the caller can post the already-rendered body itself. When configured it creates a review
 * comment via {@code POST /repos/{owner}/{repo}/pulls/{number}/comments}, anchored to the last
 * changed line (with {@code start_line} for multi-line spans) on the RIGHT side of the diff.
 */
@Component
public class GitHubSuggestionSink implements MutationSink {

    private static final Logger log = LoggerFactory.getLogger(GitHubSuggestionSink.class);

    private final GitHubProperties props;

    public GitHubSuggestionSink(GitHubProperties props) {
        this.props = props;
    }

    @Override
    public SinkResult publish(PublishContext ctx) {
        PrContext pr = ctx.pr();
        if (pr == null) {
            return SinkResult.skipped("no PR context; suggestion rendered but not posted");
        }
        if (!props.hasToken()) {
            return SinkResult.skipped("no github.token configured; suggestion rendered but not posted");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("body", ctx.suggestionBody());
        body.put("commit_id", ctx.sha());
        body.put("path", ctx.filePath());
        body.put("line", ctx.endLine());
        body.put("side", "RIGHT");
        if (ctx.startLine() < ctx.endLine()) {
            body.put("start_line", ctx.startLine());
            body.put("start_side", "RIGHT");
        }

        try {
            RestClient client = RestClient.builder().baseUrl(props.getApiUrl()).build();
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .uri("/repos/{owner}/{repo}/pulls/{number}/comments", pr.owner(), pr.repo(), pr.number())
                    .header("Authorization", "Bearer " + props.getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String url = response == null ? null : String.valueOf(response.get("html_url"));
            log.info("Posted suggestion to {}/{} PR #{} -> {}", pr.owner(), pr.repo(), pr.number(), url);
            return SinkResult.published("posted suggestion comment", url);

        } catch (Exception e) {
            log.error("Failed to post suggestion to {}/{} PR #{}", pr.owner(), pr.repo(), pr.number(), e);
            return SinkResult.failed("GitHub API error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
