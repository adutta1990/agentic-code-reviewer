# Agentic Code Reviewer

An LLM-assisted code-review pipeline for Java pull requests that does more than post opinions on a
diff — it **verifies its own fixes**. A change is only ever suggested or committed after it has been
applied to a throwaway worktree, compiled, and tested, with the build proven to stay green.

Built on Spring Boot 4 and Spring AI 2.0 (OpenAI `gpt-4o`). Each stage is an independent,
structured-output engine; a thin orchestrator composes them, and a sandbox runner with no LLM in
the loop provides the verification backbone.

---

## Why this exists

Plenty of tools post model-generated comments on a diff. The hard, valuable part is the step after:
taking a proposed fix, applying it, and confirming the code still compiles and its tests still pass
before anyone sees it. That verification loop is what separates a demo from something you can point
at your default branch. This project is built around it.

The guiding decisions:

- **Cheap gate before expensive work.** Triage runs on diffs and decides which files justify a deep,
  model-heavy review — the same presubmit-vs-postsubmit pattern large monorepos use.
- **The build is the source of truth.** Every fix is verified by an actual `mvn` compile + test run
  in an isolated worktree. Green-to-green is the acceptance criterion.
- **Safe by default.** Out of the box nothing is pushed and nothing is posted. Mutation is opt-in,
  gated, and re-checked at the write boundary.

---

## Architecture

Each stage is a standalone REST engine with structured JSON output. They compose into a pipeline but
remain independently callable and testable.

```
        ┌─────────┐   worth_review?   ┌─────────┐   findings   ┌─────────┐   patch   ┌──────────┐
  PR ─▶ │ TRIAGE  │ ────────────────▶ │ REVIEW  │ ───────────▶ │  FIX    │ ────────▶ │ SANDBOX  │
        │ (diffs) │                   │ (files) │              │(1 range)│           │ (verify) │
        └─────────┘                   └─────────┘              └─────────┘           └────┬─────┘
             gate                        find                     propose            green-to-green?
                                                                                          │ ACCEPTED
                                                                                          ▼
                                                                                    ┌───────────┐
                                                                                    │ AUTO-APPLY│
                                                                                    │  sink:    │
                                                                                    │ SUGGEST / │
                                                                                    │ COMMIT    │
                                                                                    └───────────┘
```

A separate **test author** engine (generate + build-repair modes) rides the same structured-output
pattern and, later, the same sandbox harness.

### The stages

| Stage | Endpoint | In → Out | What it does |
|---|---|---|---|
| **Triage** | `POST /api/triage` | PR diffs → per-file verdicts | Flags which changed files warrant deep review. Generated/lock/import-only files are never worth it; uncertain → flagged (false negatives are the expensive error). |
| **Review** | `POST /api/review` | one file + changed ranges → findings | Line-level review: correctness, concurrency, resource leaks, security, API/contract. Emits nothing below a confidence floor. |
| **Fix** | `POST /api/fix` | a finding + range → replacement | Produces a minimal, mechanically-applicable replacement for the target lines, or refuses (`applicable=false`). Declares `behavior_change`. |
| **Test author** | `POST /api/tests/generate`, `POST /api/tests/repair` | class + changed methods → JUnit 5 test file | Writes tests that fail on wrong code; `repair` fixes a test against a failed build without weakening assertions. |
| **PR orchestrator** | `POST /api/pull-requests/review` | whole PR → aggregated result | Triage-gate → bounded-parallel review of flagged files → order-preserving aggregate. |
| **Sandbox** | `POST /api/sandbox/run` | patch + repo SHA → green/red + test delta | **No LLM.** Verifies a patch in an isolated worktree. The verification backbone. |
| **Auto-apply** | `POST /api/auto-apply` | a fix + PR context → verified outcome | Splices the fix, verifies it in the sandbox, and only on green-to-green routes it to a sink (suggest / commit). |

---

## The verification backbone (sandbox)

`SandboxRunner` takes a patch and a commit SHA and returns a verdict — with no model involved, so it
is deterministic and unit-testable on its own.

1. Add a git worktree **pinned to the exact SHA** (a concurrent force-push to the branch is harmless).
2. Baseline: `mvn -o test-compile` then run the affected test subset via `surefire:test`.
3. Apply the patch (through a path allowlist).
4. Candidate: recompile + re-run the same tests.
5. Compare.

| Verdict | Meaning | Action |
|---|---|---|
| `ACCEPTED` | baseline green, candidate green, **identical test set** | safe to suggest/commit |
| `REJECTED` | patched code does not compile | discard, no human needed |
| `ESCALATE` | baseline not green, test set changed **in either direction**, timeout, or runner error | a human decides |

A failing test that turns green is treated as suspiciously as a passing test that breaks — both
change the test set, both escalate.

**Guardrails** (all in `sandbox.*` config): worktree deleted in a `finally`; hard wall-clock timeout
with the whole process tree killed on expiry; offline `-o` builds against a pre-warmed local repo (no
network egress); a path allowlist that forbids `pom.xml`, CI config, and anything outside the module
under review; and a global concurrent-build cap.

---

## Safety model

Nothing mutates anything external unless you opt in. Defaults:

- `code-review.auto-apply.mode: SUGGEST` — posts a one-click GitHub suggestion; never touches the branch.
- `code-review.auto-apply.allow-push: false` — commit/push sinks render but do nothing.
- `github.token` blank — suggestions are rendered and returned, but not posted.

When enabled, the commit sink still **re-checks the branch tip equals the verified SHA** before
pushing — a patch is never layered onto a tree the sandbox never saw.

Starting at `SUGGEST` is also a measurement strategy: applied-vs-dismissed rate over a few weeks is a
free precision metric. If it's under ~50%, that's a prompt problem you want to learn from ignored
suggestions, not reverted commits.

---

## Prerequisites

- **JDK 17** (the project targets Java 17).
- **OpenAI API key** — for the LLM stages (triage, review, fix, tests). The sandbox needs no key.
- **git** and **Maven** on `PATH` — the sandbox shells out to both (the Maven wrapper `./mvnw` is used
  inside worktrees automatically).
- For **offline sandbox builds**: a pre-warmed local Maven repository (see Configuration).

> The shell's default `JAVA_HOME` may point at an older JDK. Build and run with JDK 17 explicitly:
> ```bash
> export JAVA_HOME=$(/usr/libexec/java_home -v 17)
> ```

---

## Quick start

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export OPENAI_API_KEY=sk-...

./mvnw spring-boot:run
```

### Triage a PR

```bash
curl -X POST localhost:8080/api/triage \
  -H 'Content-Type: application/json' \
  -d '{
    "repo": "acme/billing",
    "baseRef": "main",
    "prTitle": "Cache invoice totals",
    "prBody": "Adds a memoized cache",
    "changedFiles": [
      {"path": "src/main/java/com/acme/InvoiceCache.java",
       "diff": "@@ -1,3 +1,7 @@\n+public class InvoiceCache { ... }"}
    ]
  }'
```

### Verify a patch (no LLM, no key needed)

```bash
curl -X POST localhost:8080/api/sandbox/run \
  -H 'Content-Type: application/json' \
  -d '{
    "repoDir": "/path/to/warm/checkout",
    "sha": "<head-sha>",
    "moduleDir": "",
    "edits": [{"path": "src/main/java/com/acme/Foo.java", "newContent": "package com.acme; ..."}],
    "testClasses": ["com.acme.FooTest"]
  }'
```

---

## Configuration

All settings live in `src/main/resources/application.yaml`.

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o

code-review:
  style-guide: "Google Java Style Guide"   # cited for style findings only
  existing-linters: [Checkstyle, SpotBugs] # the reviewer won't duplicate these
  assertion-lib: "AssertJ"                 # what the test author writes against
  min-confidence: 0.6                      # findings below this are dropped
  max-concurrent-reviews: 4                # bound on parallel per-file reviews
  auto-apply:
    mode: SUGGEST                          # SUGGEST | COMMIT | AUTOFIX_ONLY
    allow-push: false                      # master switch for pushing commits
    commit-author-name: "code-reviewer-bot"
    commit-author-email: "code-reviewer-bot@users.noreply.github.com"
    commit-message-prefix: "Auto-fix: "

sandbox:
  timeout-seconds: 600                     # per-build wall-clock limit; process tree killed on expiry
  max-concurrent-builds: 2                 # global cap on concurrent builds
  queue-timeout-seconds: 60                # wait for a build permit before escalating
  offline: true                            # -o: no network during builds
  # local-maven-repo: /path/to/prewarmed/.m2/repository
  # work-root: /tmp
  # java-home: /path/to/jdk-17             # forked builds inherit this; set if the default JDK is wrong

github:
  api-url: "https://api.github.com"
  token: ${GITHUB_TOKEN:}                  # blank = render suggestions but don't post them

management:                                # Actuator: liveness/readiness probes for orchestrators
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never                  # don't leak component detail on the wire
```

**Auto-apply modes:** `SUGGEST` posts a GitHub suggestion block; `COMMIT` commits + pushes verified
fixes; `AUTOFIX_ONLY` commits only fixes whose `behavior_change` is `NONE`.

**Offline builds:** the sandbox runs Maven with `-o`, so every dependency must already be in
`local-maven-repo`. Pre-warm it once against the target project (e.g. `mvn -Dmaven.repo.local=<dir>
dependency:go-offline`) and point `sandbox.local-maven-repo` at it.

Every property can be overridden by an environment variable via Spring's relaxed binding
(`SANDBOX_WORK_ROOT`, `CODE_REVIEW_AUTO_APPLY_MODE`, …), so nothing sensitive needs to live in the
image. See `.env.example`.

---

## Deployment

### Build & run

```bash
./mvnw -DskipTests package               # → target/agentic-code-reviewer-*.war (executable)
java -jar target/agentic-code-reviewer-*.war
```

### Container

A multi-stage `Dockerfile` is included:

```bash
docker build -t agentic-code-reviewer .
docker run --rm -p 8080:8080 \
  -e OPENAI_API_KEY=sk-... \
  -v /path/to/target/repos:/repos \
  -v /path/to/prewarmed-m2:/work/.m2/repository \
  agentic-code-reviewer
```

The runtime image ships a **full JDK plus git and Maven**, not a slim JRE — the sandbox forks
`javac` and shells out to both tools. The repositories the sandbox verifies against must be mounted
into the container (e.g. `/repos`), and for offline builds a pre-warmed Maven repo should be mounted
and pointed at via `SANDBOX_LOCAL_MAVEN_REPO`. The container runs as a non-root user with a writable
`/work` for throwaway worktrees.

### Secrets

`OPENAI_API_KEY` and the optional `GITHUB_TOKEN` are read from the environment — never baked into the
image or committed. `.env` is gitignored; use `.env.example` as the template and inject real values
through your platform's secret store.

### Health & probes

Actuator exposes liveness/readiness for orchestrators:

```
GET /actuator/health            # overall
GET /actuator/health/liveness   # k8s livenessProbe
GET /actuator/health/readiness  # k8s readinessProbe
```

Only `health` and `info` are exposed; health detail is not shown on the wire.

### API error contract

Every endpoint validates its request body (`@Valid`) and returns a uniform error body. A bad request
is a `400` naming the offending fields; an unexpected failure is a `500` carrying only a correlation
`errorId`, with the full cause logged against that id server-side — internals never reach the client.

```json
{ "timestamp": "...", "status": 400, "error": "Bad Request",
  "message": "one or more fields are invalid",
  "errorId": null,
  "fieldErrors": [ { "field": "sha", "message": "must not be blank" } ] }
```

---

## Testing

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw test
```

The suite covers the parts where a bug silently corrupts a file or publishes an unverified change,
and needs **no API key, database, or network**:

- `PatchBuilderTest` — line-range splicing (off-by-one, CRLF, trailing newline, deletion, bad ranges).
- `TestDeltasTest` — the before/after test comparison the acceptance decision rests on.
- `PathPolicyTest` — the sandbox path allowlist.
- `SurefireReportParserTest` — per-test result parsing.
- `SuggestionRendererTest` — the GitHub suggestion wire format.
- `AutoApplyServiceTest` — the gate → verify → route policy (mocked sandbox + sinks).

`AgenticCodeReviewerApplicationTests` boots the full context and is auto-skipped unless
`OPENAI_API_KEY` is set.

---

## Project layout

```
src/main/java/com/ai/agents/
├── agenticService/   TriageAgent, ReviewAgent, FixAgent, TestAgent  (LLM engines)
├── autoapply/        AutoApplyService, PatchBuilder, sinks, sandbox glue
├── config/           ReviewProperties, AutoApplyProperties, GitHubProperties
├── controllers/      one REST controller per stage
├── domain/           request/response records (structured LLM I/O, bean-validated)
├── sandbox/          SandboxRunner + git/maven/surefire/path-policy (no LLM)
├── services/         orchestration + fail-safe wrappers
└── web/              GlobalExceptionHandler + ApiError (uniform error contract)
```

---

## Production notes & honest limits

- **LLM stages** need a valid `OPENAI_API_KEY` and cost per call; they are non-deterministic. Every
  service wraps them with a fail-safe (triage fails safe → flag all; review/fix/tests fail closed →
  refuse) so a model hiccup never 500s or silently drops a file.
- **The two mutating sinks** (GitHub suggestion post, git commit/push) are real but are the only parts
  not exercised by the test suite — they require a live GitHub endpoint and a real remote. Smoke-test
  the commit sink against a throwaway branch with `allow-push=true` before trusting it.
- **Baseline builds are recomputed each run.** The baseline is a pure function of
  `(sha, module, testClasses)` and is cacheable — worth adding when wiring to real PR volume.

### Roadmap — what makes this a product

This is a verified engine set with manual, hand-driven REST entry points. It is deployable to an
**internal, authenticated, operator-driven** setting today. It is **not yet a turnkey code-review
bot**. Before that, in rough priority:

1. **PR ingestion (GitHub App + webhook).** Nothing listens for PRs — everything is manual REST calls.
   This is the missing "front door," and it forces the end-to-end wiring below.
2. **API authentication/authorization.** The endpoints are currently open. `/api/sandbox/run` runs
   Maven on a caller-supplied path and `/api/auto-apply` can push commits — these must be locked down
   (the webhook path derives the repo server-side, removing that risk).
3. **End-to-end pipeline wiring.** `triage → review` is orchestrated; `review → fix → verify → apply`
   is not yet composed into a single per-PR flow.
4. **Cost/rate controls on LLM calls** (per-PR token budget, size caps, backoff) and **metrics**
   (Micrometer counters for verdicts, findings, tokens).
5. **Verify the two mutating sinks** against a live GitHub endpoint and remote.
6. **The automated test-repair loop** (write test → `mvn test` → feed failures to
   `/api/tests/repair` → retry) — rides this same sandbox harness.
