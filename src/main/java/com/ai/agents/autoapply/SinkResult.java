package com.ai.agents.autoapply;

/**
 * What a {@link MutationSink} did with a verified fix.
 *
 * @param status    published, skipped (not configured), or failed.
 * @param detail    human-readable explanation.
 * @param reference a URL (posted comment) or commit SHA (pushed change), when applicable.
 */
public record SinkResult(Status status, String detail, String reference) {

    public enum Status {PUBLISHED, SKIPPED, FAILED}

    public static SinkResult published(String detail, String reference) {
        return new SinkResult(Status.PUBLISHED, detail, reference);
    }

    public static SinkResult skipped(String detail) {
        return new SinkResult(Status.SKIPPED, detail, null);
    }

    public static SinkResult failed(String detail) {
        return new SinkResult(Status.FAILED, detail, null);
    }
}
