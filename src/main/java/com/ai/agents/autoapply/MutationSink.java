package com.ai.agents.autoapply;

/** A destination for a verified fix: a GitHub suggestion, a pushed commit, etc. */
public interface MutationSink {

    /** Publish the fix. Implementations never throw for expected failures — they return a result. */
    SinkResult publish(PublishContext context);
}
