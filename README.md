# Incident Management Agent

A Spring AI agent that diagnoses production incidents and either resolves them or produces the
best available resolution. It retrieves real runbooks from a pgvector store (RAG), gathers live
evidence through diagnostic tools, and returns a structured verdict.

- **Model:** OpenAI `gpt-4o` (chat) + `text-embedding-3-small` (embeddings)
- **Vector store:** pgvector (Postgres 17)
- **Corpus:** 108 production runbooks from [prometheus-operator/runbooks](https://github.com/prometheus-operator/runbooks) (Apache-2.0)

---

## Quick start

```bash
# 1. Runbook corpus (already downloaded; re-run to refresh)
./scripts/download-corpus.sh

# 2. pgvector
docker compose up -d

# 3. API key
export OPENAI_API_KEY=sk-...

# 4. JDK 21 is required
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# 5. Run (first boot embeds 108 runbooks — ~30s, ~108 embedding calls, a few cents)
./mvnw spring-boot:run
```


### Try it

```bash
curl -X POST localhost:8080/api/incidents/analyze \
  -H 'Content-Type: application/json' \
  -d '{"alertName":"KubePodCrashLooping","service":"checkout-api","namespace":"production",
       "description":"Pods restarting repeatedly since 14:10"}'
```

The bundled simulator has three scenarios, chosen by service name, so you can watch the agent
reach a genuinely different conclusion for each:

| Service          | Planted failure                          | The correct diagnosis                                    |
|------------------|------------------------------------------|----------------------------------------------------------|
| `checkout-api`   | OOMKilled crash loop (exit 137)          | Memory limit too low. A restart only buys minutes.       |
| `payments-api`   | Bad deploy — `v5.3.0` config parse error | Roll back to `v5.2.0`; errors start at the rollout.       |
| `search-indexer` | Disk at 96% and climbing                 | Reclaim disk / expand the volume.                         |
| anything else    | healthy                                  | Nothing to fix.                                           |

`payments-api` is the interesting one: the fix is only visible if the agent checks deployment
history and correlates it with the error onset.

---

## How it works

```
Incident ──▶ QuestionAnswerAdvisor ──▶ pgvector: top-4 runbooks by cosine similarity
                    │
                    ▼
              gpt-4o + tools ──▶ DiagnosticTools   (read-only, ungated)
                    │        └──▶ RemediationTools (mutating, GATED)
                    ▼
           IncidentResolution (structured) + audit trail ──▶ IncidentReport
```

Spring AI 2.0 auto-registers the `ToolCallingAdvisor`, so the tool-call loop is not hand-written.

### Design decisions worth knowing

**The corpus is not chunked.** The reflex is to run a `TokenTextSplitter` over everything. That
would be wrong here: each runbook is a small, tightly-structured document (Meaning / Impact /
Diagnosis / Mitigation) whose sections only make sense together. Splitting yields fragments like a
bare "## Mitigation" that retrieves on the word "mitigation" but is useless without its alert, and
lets one runbook occupy every top-k slot with its own pieces. The retrieval unit is the whole
runbook. Chunking would be right for long-form postmortems — not for this corpus.

**The safety gate is Java, not prompting.** `incident-agent.remediation.auto-remediate` defaults to
`false`, and every mutating tool checks it before touching anything. This is deliberate: a prompt
instruction like *"do not restart without permission"* is a suggestion a model can be argued out of
by a cleverly-worded incident description; an `if` statement in front of the infrastructure call
cannot. The prompt still describes the policy, but only so the model produces sensible
recommendations — it is not what enforces it. That is also why the gate is unit-testable
(`RemediationGateTest`, 7 tests, no DB or API key needed) — you cannot write that test against a
prompt.

**Refusals are recorded, not just successes.** The audit trail captures what the agent *wanted* to
do. That is the signal you use to decide whether to widen the allowlist.

**The model's claims and the system's record are kept separate.** `IncidentReport.resolution` is
what the model says it did; `IncidentReport.actionsTaken` is what actually happened. If they
disagree, you can see it.

---

## Configuration

```yaml
incident-agent:
  ingestion:
    enabled: true
    force: false            # true = re-embed the corpus (costs money)
  retrieval:
    top-k: 4
    similarity-threshold: 0.35
  remediation:
    auto-remediate: false   # THE SAFETY GATE — leave false until you trust it
    allowed-actions:        # allowlist, applies only when auto-remediate: true
      - RESTART_WORKLOAD
      - SCALE_WORKLOAD
      # ROLLBACK_DEPLOYMENT deliberately omitted — disruptive, wants a human
```

Both conditions must hold for the agent to mutate anything: `auto-remediate: true` **and** the
action is allowlisted. With `auto-remediate: false` the agent still does the full diagnosis and
returns the fix as recommended steps with exact commands — it just doesn't run them.

---

## Going to production

The pieces you'd replace, in order:

1. **`SimulatedInfrastructureClient`** — the only fake. Implement `InfrastructureClient` against
   the Kubernetes Java client and your metrics backend, mark it `@Primary`, and nothing else
   changes. Every action the agent can take on real systems passes through that one interface,
   which makes it the place to audit blast radius.
2. **`RemediationAudit`** — currently in-memory. Point `record()` at a durable audit sink.
3. **`POST /api/incidents/analyze`** is synchronous and takes seconds (several model round-trips).
   For a real alerting webhook, hand off to a queue rather than blocking the caller.
4. **Add your own runbooks** to `src/main/resources/rag-corpus/runbooks/` as
   `<component>__<AlertName>.md` and run with `ingestion.force=true`. Your internal runbooks will
   outperform the generic ones — this corpus is a starting point, not the destination.

### Threading constraint

`RemediationAudit` uses a `ThreadLocal`, valid because Spring AI's blocking `.call()` path runs
tool callbacks on the calling thread. If you move to the reactive `.stream()` path or execute tools
on a separate pool, that must become an explicit context object threaded through the tool beans.

---

## Tests

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw test
```

`RemediationGateTest` (7 tests) covers the safety gate and needs no database, API key, or model.
`AiAgentsApplicationTests` boots the full context and is auto-skipped unless `OPENAI_API_KEY` is
set and pgvector is up.

## Layout

```
docker-compose.yml                      pgvector
scripts/download-corpus.sh              fetches the runbook corpus
src/main/resources/rag-corpus/runbooks/ 108 runbooks (downloaded)
src/main/java/com/ai/agents/
├── agenticService/IncidentManagementAgent.java   RAG + tools + system prompt
├── config/IncidentAgentProperties.java           incident-agent.* binding
├── controllers/IncidentManagementController.java REST + Alertmanager webhook
├── domain/                                       Incident, IncidentResolution, IncidentReport
├── infra/InfrastructureClient.java               the swap point for real infrastructure
├── ingestion/RunbookIngestionService.java        corpus → pgvector
├── services/IncidentManagementService.java       orchestration + failure handling
└── tools/                                        DiagnosticTools, RemediationTools, the gate
```
