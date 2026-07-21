package com.ai.agents.tools;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Records every mutating action the agent attempted, including the ones that were refused.
 *
 * <p>The refusals matter as much as the successes: they are the evidence that the gate held,
 * and they tell an operator exactly what the agent <em>wanted</em> to do — which is the
 * signal you use to decide whether to widen the allowlist.
 *
 * <h2>Scoping</h2>
 * The trail is per-<em>analysis</em>, not per-request. That distinction is load-bearing: the
 * Alertmanager webhook analyzes several alerts inside one HTTP request, so a request-scoped
 * trail would attribute the first alert's actions to every later report in the batch. It is
 * also not a singleton, which would leak actions across unrelated incidents entirely.
 *
 * <p>So state lives in a {@link ThreadLocal}, bracketed by {@link #start()} and {@link #clear()}.
 * This holds because an analysis is synchronous — Spring AI's blocking {@code .call()} path
 * executes tool callbacks on the calling thread. If you move to the reactive {@code .stream()}
 * path or execute tools on a separate pool, this must become an explicit context object
 * threaded through the tool beans instead.
 *
 * <p>In-memory only; point {@link #record} at your audit sink for production use.
 */
@Component
public class RemediationAudit {

    private final ThreadLocal<List<Entry>> entries = ThreadLocal.withInitial(ArrayList::new);

    /** Begin a fresh trail for one analysis. */
    public void start() {
        entries.get().clear();
    }

    public void record(Entry entry) {
        entries.get().add(entry);
    }

    /** Snapshot of this analysis's trail. */
    public List<Entry> entries() {
        return List.copyOf(entries.get());
    }

    /** Release the thread's trail. Call in a finally block — threads are pooled and reused. */
    public void clear() {
        entries.remove();
    }

    /**
     * @param action  what was attempted.
     * @param target  namespace/workload it was aimed at.
     * @param params  action arguments, e.g. {replicas=5}.
     * @param outcome APPLIED, REFUSED, or FAILED.
     * @param detail  human-readable result or refusal reason.
     * @param at      when it happened.
     */
    public record Entry(
            RemediationAction action,
            String target,
            Map<String, Object> params,
            Outcome outcome,
            String detail,
            Instant at) {

        public static Entry applied(RemediationAction action, String target, Map<String, Object> params, String detail) {
            return new Entry(action, target, params, Outcome.APPLIED, detail, Instant.now());
        }

        public static Entry refused(RemediationAction action, String target, Map<String, Object> params, String reason) {
            return new Entry(action, target, params, Outcome.REFUSED, reason, Instant.now());
        }

        public static Entry failed(RemediationAction action, String target, Map<String, Object> params, String detail) {
            return new Entry(action, target, params, Outcome.FAILED, detail, Instant.now());
        }
    }

    public enum Outcome {
        /** The action ran and succeeded. */
        APPLIED,
        /** The gate blocked it; nothing was changed. */
        REFUSED,
        /** The action ran and failed. */
        FAILED
    }
}
