package com.ai.agents.services;

import com.ai.agents.agenticService.ReviewAgent;
import com.ai.agents.config.ReviewProperties;
import com.ai.agents.domain.FileReviewResult;
import com.ai.agents.domain.ReviewFinding;
import com.ai.agents.domain.ReviewRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates a single file review: run the engine, enforce the confidence floor as a Java
 * safety net, and degrade to an empty (clean) review on model failure rather than 500-ing.
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewAgent agent;
    private final ReviewProperties props;

    public ReviewService(ReviewAgent agent, ReviewProperties props) {
        this.agent = agent;
        this.props = props;
    }

    public FileReviewResult review(ReviewRequest request) {
        long start = System.currentTimeMillis();
        try {
            FileReviewResult result = agent.review(request);

            // The prompt tells the model to emit nothing below the floor; enforce it here too so
            // a model that ignores the instruction can't post a low-confidence comment publicly.
            List<ReviewFinding> kept = result.findings().stream()
                    .filter(f -> f.confidence() >= props.getMinConfidence())
                    .toList();

            int dropped = result.findings().size() - kept.size();
            if (dropped > 0) {
                log.info("Dropped {} finding(s) below confidence floor {}", dropped, props.getMinConfidence());
            }
            log.info("Review succeeded in {}ms: {} finding(s) posted",
                    System.currentTimeMillis() - start, kept.size());
            return new FileReviewResult(result.path(), kept);

        } catch (Exception e) {
            // A reviewer that 500s blocks the PR check. Fail open to a clean review: better to
            // post nothing than to fail the pipeline. Triage already flagged this file as worth a
            // human look, so a missed automated comment is not a silent miss.
            log.error("Review failed after {}ms; returning empty review",
                    System.currentTimeMillis() - start, e);
            return new FileReviewResult(request.path(), List.of());
        }
    }
}
