package com.ai.agents.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A fake cluster with a few realistic failure scenarios baked in, so the agent can be
 * exercised end-to-end without real infrastructure.
 *
 * <p>Scenarios are keyed by workload name. A workload whose name contains a scenario key
 * exhibits that failure; anything else looks healthy. This lets you drive the agent with
 * a plain HTTP call and watch it reach a correct conclusion:
 *
 * <ul>
 *   <li>{@code checkout-api}  &rarr; OOMKilled crash loop (memory limit too low)</li>
 *   <li>{@code payments-api}  &rarr; bad deploy; errors began at the last rollout</li>
 *   <li>{@code search-indexer} &rarr; disk filling up</li>
 *   <li>anything else          &rarr; healthy</li>
 * </ul>
 *
 * <p>Mutations are recorded in memory and reflected in later reads, so a restart the agent
 * performs actually shows up if it re-checks status.
 *
 * <p>To swap in real infrastructure, implement {@link InfrastructureClient} and annotate
 * your bean {@code @Primary} — it will take precedence over this one without any change here.
 */
@Component
public class SimulatedInfrastructureClient implements InfrastructureClient {

    private static final Logger log = LoggerFactory.getLogger(SimulatedInfrastructureClient.class);

    /** Mutations applied during this run, so reads after a fix reflect the fix. */
    private final Map<String, Integer> replicaOverrides = new ConcurrentHashMap<>();
    private final Map<String, Boolean> restarted = new ConcurrentHashMap<>();
    private final Map<String, Boolean> rolledBack = new ConcurrentHashMap<>();

    private enum Scenario {OOM_CRASHLOOP, BAD_DEPLOY, DISK_FILLING, HEALTHY}

    private Scenario scenarioFor(String workload) {
        String w = workload == null ? "" : workload.toLowerCase(Locale.ROOT);
        if (w.contains("checkout")) return Scenario.OOM_CRASHLOOP;
        if (w.contains("payment")) return Scenario.BAD_DEPLOY;
        if (w.contains("search") || w.contains("index")) return Scenario.DISK_FILLING;
        return Scenario.HEALTHY;
    }

    private String key(String ns, String workload) {
        return ns + "/" + workload;
    }

    @Override
    public WorkloadStatus getWorkloadStatus(String namespace, String workload) {
        log.info("[sim] getWorkloadStatus {}/{}", namespace, workload);
        String k = key(namespace, workload);
        Scenario scenario = scenarioFor(workload);

        // A restart clears an OOM crash loop only briefly — the memory limit is still wrong,
        // so the agent should not treat "restarted" as a resolution. A rollback genuinely
        // fixes the bad-deploy scenario.
        boolean wasRestarted = restarted.getOrDefault(k, false);
        boolean wasRolledBack = rolledBack.getOrDefault(k, false);
        int desired = replicaOverrides.getOrDefault(k, 3);

        return switch (scenario) {
            case OOM_CRASHLOOP -> new WorkloadStatus(
                    workload, namespace, desired,
                    wasRestarted ? desired : 0,
                    wasRestarted ? 2 : 47,
                    "137",
                    "OOMKilled",
                    wasRestarted
                            ? List.of("Running", "Running", "Running")
                            : List.of("CrashLoopBackOff", "CrashLoopBackOff", "Running"),
                    "v2.14.1");
            case BAD_DEPLOY -> new WorkloadStatus(
                    workload, namespace, desired,
                    wasRolledBack ? desired : 1,
                    wasRolledBack ? 0 : 3,
                    wasRolledBack ? "0" : "1",
                    wasRolledBack ? "None" : "Error",
                    wasRolledBack
                            ? List.of("Running", "Running", "Running")
                            : List.of("Running", "Error", "Error"),
                    wasRolledBack ? "v5.2.0" : "v5.3.0");
            case DISK_FILLING -> new WorkloadStatus(
                    workload, namespace, desired, desired, 0, "0", "None",
                    List.of("Running", "Running", "Running"), "v1.9.3");
            case HEALTHY -> new WorkloadStatus(
                    workload, namespace, desired, desired, 0, "0", "None",
                    List.of("Running", "Running", "Running"), "v1.0.0");
        };
    }

    @Override
    public List<String> getRecentLogs(String namespace, String workload, int lines) {
        log.info("[sim] getRecentLogs {}/{} lines={}", namespace, workload, lines);
        String k = key(namespace, workload);
        List<String> logs = new ArrayList<>();
        Instant t = Instant.now().minus(3, ChronoUnit.MINUTES);

        switch (scenarioFor(workload)) {
            case OOM_CRASHLOOP -> {
                logs.add(t + " INFO  Starting " + workload + " v2.14.1");
                logs.add(t.plusSeconds(4) + " INFO  Loading product catalog into in-memory cache");
                logs.add(t.plusSeconds(31) + " WARN  Cache size 1.6GB exceeds soft limit 1.0GB");
                logs.add(t.plusSeconds(38) + " WARN  GC pressure high: 87% time in GC");
                if (!restarted.getOrDefault(k, false)) {
                    logs.add(t.plusSeconds(41) + " ERROR java.lang.OutOfMemoryError: Java heap space");
                    logs.add(t.plusSeconds(41) + " FATAL Container terminated (exit 137, OOMKilled)");
                }
            }
            case BAD_DEPLOY -> {
                if (rolledBack.getOrDefault(k, false)) {
                    logs.add(t + " INFO  Starting " + workload + " v5.2.0");
                    logs.add(t.plusSeconds(9) + " INFO  Connected to ledger-db pool=20");
                    logs.add(t.plusSeconds(15) + " INFO  Health check OK");
                } else {
                    logs.add(t + " INFO  Starting " + workload + " v5.3.0");
                    logs.add(t.plusSeconds(6) + " ERROR Failed to parse config key 'ledger.timeout.ms': expected int, got '30s'");
                    logs.add(t.plusSeconds(6) + " ERROR NumberFormatException: For input string: \"30s\"");
                    logs.add(t.plusSeconds(7) + " FATAL Application startup failed; shutting down");
                }
            }
            case DISK_FILLING -> {
                logs.add(t + " INFO  Index segment merge started");
                logs.add(t.plusSeconds(20) + " WARN  Free disk on /var/lib/index at 8.1% and falling");
                logs.add(t.plusSeconds(45) + " WARN  Segment merge paused: insufficient scratch space");
                logs.add(t.plusSeconds(60) + " ERROR Cannot flush index: No space left on device");
            }
            case HEALTHY -> {
                logs.add(t + " INFO  Health check OK");
                logs.add(t.plusSeconds(30) + " INFO  Processed 1,204 requests p99=42ms");
            }
        }
        return logs.size() > lines ? logs.subList(logs.size() - lines, logs.size()) : logs;
    }

    @Override
    public MetricSample getMetric(String namespace, String workload, String metricName) {
        log.info("[sim] getMetric {}/{} metric={}", namespace, workload, metricName);
        String m = metricName == null ? "" : metricName.toLowerCase(Locale.ROOT);
        Scenario scenario = scenarioFor(workload);

        if (m.contains("mem")) {
            return switch (scenario) {
                case OOM_CRASHLOOP -> new MetricSample(metricName, 99.2, "percent", "rising, hit limit 4 times in last 20m");
                default -> new MetricSample(metricName, 46.5, "percent", "stable");
            };
        }
        if (m.contains("cpu")) {
            return new MetricSample(metricName, scenario == Scenario.OOM_CRASHLOOP ? 88.0 : 31.0, "percent",
                    scenario == Scenario.OOM_CRASHLOOP ? "elevated (GC thrash)" : "stable");
        }
        if (m.contains("disk")) {
            return switch (scenario) {
                case DISK_FILLING -> new MetricSample(metricName, 96.4, "percent", "rising ~2%/hour; full in ~2h");
                default -> new MetricSample(metricName, 38.0, "percent", "stable");
            };
        }
        if (m.contains("error") || m.contains("5xx")) {
            return switch (scenario) {
                case BAD_DEPLOY -> new MetricSample(metricName, 62.0, "percent", "spiked at 14:02, coincides with v5.3.0 rollout");
                case OOM_CRASHLOOP -> new MetricSample(metricName, 18.0, "percent", "correlates with pod restarts");
                default -> new MetricSample(metricName, 0.2, "percent", "stable");
            };
        }
        return new MetricSample(metricName, 0.0, "unknown", "no data for this metric");
    }

    @Override
    public List<DeploymentEvent> getDeploymentHistory(String namespace, String workload) {
        log.info("[sim] getDeploymentHistory {}/{}", namespace, workload);
        boolean wasRolledBack = rolledBack.getOrDefault(key(namespace, workload), false);

        return switch (scenarioFor(workload)) {
            case BAD_DEPLOY -> List.of(
                    new DeploymentEvent("47", "v5.3.0", Instant.now().minus(22, ChronoUnit.MINUTES).toString(), "ci-bot", !wasRolledBack),
                    new DeploymentEvent("46", "v5.2.0", Instant.now().minus(6, ChronoUnit.DAYS).toString(), "ci-bot", wasRolledBack),
                    new DeploymentEvent("45", "v5.1.4", Instant.now().minus(21, ChronoUnit.DAYS).toString(), "ci-bot", false));
            case OOM_CRASHLOOP -> List.of(
                    new DeploymentEvent("31", "v2.14.1", Instant.now().minus(9, ChronoUnit.DAYS).toString(), "ci-bot", true),
                    new DeploymentEvent("30", "v2.14.0", Instant.now().minus(17, ChronoUnit.DAYS).toString(), "ci-bot", false));
            default -> List.of(
                    new DeploymentEvent("12", "v1.0.0", Instant.now().minus(30, ChronoUnit.DAYS).toString(), "ci-bot", true));
        };
    }

    @Override
    public ActionResult restartWorkload(String namespace, String workload) {
        log.warn("[sim] MUTATION restartWorkload {}/{}", namespace, workload);
        restarted.put(key(namespace, workload), true);
        return new ActionResult(true, "Rolling restart of %s/%s completed; all pods Running.".formatted(namespace, workload));
    }

    @Override
    public ActionResult scaleWorkload(String namespace, String workload, int replicas) {
        log.warn("[sim] MUTATION scaleWorkload {}/{} -> {}", namespace, workload, replicas);
        if (replicas < 0 || replicas > 100) {
            return new ActionResult(false, "Refused: replicas must be between 0 and 100, got " + replicas);
        }
        replicaOverrides.put(key(namespace, workload), replicas);
        return new ActionResult(true, "Scaled %s/%s to %d replicas.".formatted(namespace, workload, replicas));
    }

    @Override
    public ActionResult rollbackDeployment(String namespace, String workload) {
        log.warn("[sim] MUTATION rollbackDeployment {}/{}", namespace, workload);
        rolledBack.put(key(namespace, workload), true);
        return new ActionResult(true, "Rolled %s/%s back to the previous revision.".formatted(namespace, workload));
    }
}
