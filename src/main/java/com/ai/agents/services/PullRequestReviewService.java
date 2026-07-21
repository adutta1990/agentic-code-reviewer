package com.ai.agents.services;

import com.ai.agents.config.ReviewProperties;
import com.ai.agents.domain.FileReviewResult;
import com.ai.agents.domain.PullRequestReviewRequest;
import com.ai.agents.domain.PullRequestReviewResult;
import com.ai.agents.domain.PullRequestReviewResult.FileOutcome;
import com.ai.agents.domain.ReviewableFile;
import com.ai.agents.domain.TriageVerdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The triage → review pipeline over a whole PR.
 *
 * <p>Thin orchestration only, matching how analysis platforms compose independent analyzers:
 * cheap triage gates every file, then the expensive line-level reviewer fans out — in bounded
 * parallel — over just the files triage flagged. This class owns fan-out, concurrency, and
 * aggregation; the two analyzers ({@link TriageService}, {@link ReviewService}) stay untouched
 * and independently callable, and each already fails safe on its own, so a single bad file
 * never sinks the run.
 */
@Service
public class PullRequestReviewService {

    private static final Logger log = LoggerFactory.getLogger(PullRequestReviewService.class);

    private final TriageService triageService;
    private final ReviewService reviewService;
    private final ReviewProperties props;

    public PullRequestReviewService(
            TriageService triageService, ReviewService reviewService, ReviewProperties props) {
        this.triageService = triageService;
        this.reviewService = reviewService;
        this.props = props;
    }

    public PullRequestReviewResult review(PullRequestReviewRequest request) {
        long start = System.currentTimeMillis();

        // 1. Gate: triage every changed file from its diff.
        List<TriageVerdict> verdicts = triageService.triage(request.toPullRequest());
        Map<String, TriageVerdict> verdictByPath = verdicts.stream()
                .collect(Collectors.toMap(TriageVerdict::path, Function.identity(), (a, b) -> a));

        // 2. Select the files worth the expensive review. A file with no triage verdict is
        //    reviewed anyway — the conservative default, matching triage's own fail-safe.
        List<ReviewableFile> toReview = request.files().stream()
                .filter(f -> worthReviewing(verdictByPath.get(f.path())))
                .toList();
        log.info("PR triage: {} of {} files flagged for review",
                toReview.size(), request.files().size());

        // 3. Fan out the reviews in bounded parallel.
        Map<String, FileReviewResult> reviewByPath = fanOut(request, toReview);

        // 4. Aggregate, preserving the submitted file order.
        List<FileOutcome> outcomes = request.files().stream()
                .map(f -> new FileOutcome(
                        f.path(),
                        reviewByPath.containsKey(f.path()),
                        verdictByPath.get(f.path()),
                        reviewByPath.get(f.path())))
                .toList();

        int totalFindings = reviewByPath.values().stream()
                .mapToInt(r -> r.findings().size())
                .sum();

        log.info("PR review complete in {}ms: {} file(s), {} reviewed, {} finding(s)",
                System.currentTimeMillis() - start, request.files().size(), toReview.size(), totalFindings);

        return new PullRequestReviewResult(
                request.repo(), request.files().size(), toReview.size(), totalFindings, outcomes);
    }

    private static boolean worthReviewing(TriageVerdict verdict) {
        return verdict == null || verdict.worthReview();
    }

    /**
     * Review the selected files concurrently, capped at {@code maxConcurrentReviews} so a large
     * PR cannot open one model call per file at once. Returns results keyed by path. Each
     * {@link ReviewService#review} call already fails open, so no future here completes
     * exceptionally.
     */
    private Map<String, FileReviewResult> fanOut(
            PullRequestReviewRequest request, List<ReviewableFile> toReview) {
        if (toReview.isEmpty()) {
            return Map.of();
        }
        int concurrency = Math.max(1, Math.min(props.getMaxConcurrentReviews(), toReview.size()));
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            List<CompletableFuture<Map.Entry<String, FileReviewResult>>> futures = toReview.stream()
                    .map(file -> CompletableFuture.supplyAsync(
                            () -> Map.entry(file.path(), reviewService.review(request.toReviewRequest(file))),
                            pool))
                    .toList();

            return futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
        } finally {
            pool.shutdown();
        }
    }
}
