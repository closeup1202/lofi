# lofi

Git-powered latency diff for your backend.

lofi is a Git-powered observability tool for detecting latency regressions.  
Add one dependency, deploy — and method-level latency diffs are generated automatically.

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?style=flat-square)
![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)
![Maven Central](https://img.shields.io/maven-central/v/io.github.closeup1202/lofi-spring-boot-starter)

---

## The Problem

p99 latency spiked after your last deploy.  
To find which method caused it? Open Grafana, check Jaeger, dig through logs, read the commit diff — and connect the dots yourself, every time.

lofi links deploy events to code and shows you the diff directly.

Traditional observability tools show you what is slow.
lofi shows you what changed.

---

## What It Looks Like

```bash
$ lofi diff a3f9c1..d82e04

Deploy Diff  main@a3f9c1 → main@d82e04
───────────────────────────────────────────────────────────────
  Method                               Before    After    Delta
───────────────────────────────────────────────────────────────
  OrderService.createOrder()           14ms  →  91ms   +77ms  ▲
  PaymentClient.validate()             22ms  →  58ms   +36ms  ▲
  UserService.findById()                3ms  →   3ms      —
  ProductService.getStock()             8ms  →   9ms      —
───────────────────────────────────────────────────────────────
  2 regressions detected
```

---

## Two Deployment Modes

lofi supports two modes depending on how your app is instrumented.

| | Actuator mode | Backend mode |
|---|---|---|
| **Instrumentation** | `lofi-spring-boot-starter` (AOP) | OpenTelemetry Java Agent |
| **Data collection** | In-process (Spring AOP) | `lofi-otelcol` → `lofi-backend` |
| **Setup** | One dependency | `lofi-otelcol` binary + `lofi-backend` server |
| **Best for** | Spring Boot apps only | Any JVM app, polyglot stacks |

The CLI detects the mode automatically from the `--url` target — no extra flags needed.

---

## Actuator Mode (Spring Boot AOP)

### 1. Add the dependency

**Gradle**

```groovy
implementation 'io.github.closeup1202:lofi-spring-boot-starter:0.4.2'
```

**Maven**

```xml
<dependency>
  <groupId>io.github.closeup1202</groupId>
  <artifactId>lofi-spring-boot-starter</artifactId>
  <version>0.4.2</version>
</dependency>
```

### 2. Inject the commit hash

Deploy boundaries are detected via the `GIT_COMMIT_HASH` environment variable.

**Local development**

```bash
export GIT_COMMIT_HASH=$(git rev-parse --short HEAD)
./gradlew bootRun
```

**Docker**

```dockerfile
ARG GIT_COMMIT_HASH
ENV GIT_COMMIT_HASH=${GIT_COMMIT_HASH}
```

```bash
docker build \
  --build-arg GIT_COMMIT_HASH=$(git rev-parse --short HEAD) \
  -t my-app .
```

**GitHub Actions**

```yaml
- name: Run application
  env:
    GIT_COMMIT_HASH: ${{ github.sha }}
  run: ./gradlew bootRun
```

**Docker Compose**

```yaml
services:
  app:
    build:
      context: .
      args:
        GIT_COMMIT_HASH: ${GIT_COMMIT_HASH}
    environment:
      - GIT_COMMIT_HASH=${GIT_COMMIT_HASH}
```

```bash
GIT_COMMIT_HASH=$(git rev-parse --short HEAD) docker compose up
```

### 3. Expose the actuator endpoint

```yaml
management:
  endpoints:
    web:
      exposure:
        include: lofi
```

> **If your application uses Spring Security**, the actuator endpoints are blocked by default.
> Choose one of the following approaches.

**Option A — Management port separation (recommended)**

```yaml
management:
  server:
    port: 9090
  endpoints:
    web:
      exposure:
        include: lofi
```

```bash
lofi diff a3f9c1..d82e04 --url http://localhost:9090
```

**Option B — Permit only the lofi paths**

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/lofi/**").permitAll()
        .anyRequest().authenticated()
    );
    return http.build();
}
```

> Avoid `permitAll()` on the entire `/actuator/**` path — endpoints such as
> `/actuator/env` and `/actuator/heapdump` can leak sensitive information.

### 4. Install lofi-cli and run

```bash
npm install -g @closeup1202/lofi-cli
lofi diff a3f9c1..d82e04 --url http://localhost:8080
```

---

## Backend Mode (OpenTelemetry Pipeline)

Backend mode collects traces via the OpenTelemetry Java Agent, so no library dependency is needed in your app.

### Architecture

```
Your App (OTel Agent)
    │  OTLP (HTTP :4318 / gRPC :4317)
    ▼
lofi-otelcol  (custom OTel Collector)
    │  POST /lofi/ingest
    ▼
lofi-backend  (REST API :9292)
    │
    ▼  lofi-cli
```

### Option A — Docker Compose (recommended)

```bash
# Required: pick any secret string and export it.
# Both lofi-backend (validation) and lofi-otelcol (ingest header) read this.
export LOFI_API_KEY=$(openssl rand -hex 32)

# Start lofi-backend + lofi-otelcol
docker compose up

# Run your app with the OTel agent
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318 \
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf \
OTEL_RESOURCE_ATTRIBUTES=deployment.commit.hash=$(git rev-parse --short HEAD) \
OTEL_TRACES_SAMPLER=always_on \
OTEL_METRICS_EXPORTER=none \
OTEL_LOGS_EXPORTER=none \
java -javaagent:opentelemetry-javaagent.jar -jar your-app.jar
```

```bash
# Query via CLI
lofi diff a3f9c1..d82e04 --url http://localhost:9292
```

### Option B — Binary install

```bash
# Required: shared secret read by both backend and collector
export LOFI_API_KEY=$(openssl rand -hex 32)

# Install lofi-otelcol
curl -fsSL https://raw.githubusercontent.com/closeup1202/lofi/main/install.sh | sh

# Start lofi-backend (LOFI_API_KEY must be set, see Security section)
./gradlew :lofi-backend:bootRun

# Start the collector
lofi-otelcol --config collector-config.yaml
```

`collector-config.yaml`:

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:
    timeout: 5s

exporters:
  lofi:
    backend_url: http://localhost:9292
    api_key: ${env:LOFI_API_KEY}

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch]
      exporters: [lofi]
```

---

## CLI Usage

### diff — compare performance between two deploys

```bash
lofi diff <base>..<head> --url <url>
lofi diff                             # interactive commit selector
```

```bash
lofi diff a3f9c1..d82e04 --url http://localhost:8080   # actuator mode
lofi diff a3f9c1..d82e04 --url http://localhost:9292   # backend mode
```

### snapshot — view metrics for a specific deploy

```bash
lofi snapshot <commitHash> --url <url>
lofi snapshot                         # interactive commit selector
```

### check — CI gate (fail if regression exceeds threshold)

```bash
lofi check <base>..<head> --threshold-ms <ms> --url <url>
lofi check <base>..<head> --threshold-rate <rate> --url <url>
```

Exits with code `1` if any method exceeds the threshold — designed to fail a CI step automatically.

`lofi check` is a **post-deploy gate**, not a pre-deploy check. Both commits must already be deployed and have metrics collected before running it.

| Condition | Exit code |
|---|---|
| Regression detected | `1` |
| No regression | `0` |
| Metrics not found for a commit | `0` (warning printed) |

If metrics are missing for either commit, `lofi check` prints a warning and exits `0` rather than failing — so a missing deploy never blocks CI unintentionally.

Use `--format json` to parse results programmatically, or `--format markdown` to post a report to a PR comment:

```yaml
# Fail the step on regression
- name: Check latency regression
  env:
    BASE: ${{ github.event.pull_request.base.sha }}
    HEAD: ${{ github.event.pull_request.head.sha }}
  run: lofi check $BASE..$HEAD --threshold-ms 50 --url https://staging.myapp.com

# Post a markdown report as a PR comment
- name: Post regression report
  env:
    BASE: ${{ github.event.pull_request.base.sha }}
    HEAD: ${{ github.event.pull_request.head.sha }}
  run: |
    lofi check $BASE..$HEAD --threshold-ms 50 --format markdown \
      --url https://staging.myapp.com > report.md || true
    gh pr comment ${{ github.event.pull_request.number }} --body-file report.md
```

### Drop-in GitHub Actions workflows

Ready-to-use workflow files for both deployment modes are in [`examples/github-actions/`](./examples/github-actions/):

- [`lofi-regression-check-backend.yml`](./examples/github-actions/lofi-regression-check-backend.yml) — for `lofi-backend` users
- [`lofi-regression-check-actuator.yml`](./examples/github-actions/lofi-regression-check-actuator.yml) — for `lofi-actuator` users

Copy the file matching your setup into `.github/workflows/`, set the required secret, and PRs will automatically be checked for latency regressions.

The `--url` flag defaults to `http://localhost:8080`. The CLI auto-detects whether the target is a `lofi-backend` instance or a `lofi-actuator` endpoint — no extra configuration needed.

---

## API Reference

Both modes expose the same logical API. Paths differ by prefix.

| Operation | Actuator | Backend |
|---|---|---|
| List commits | `GET /actuator/lofi` | `GET /lofi` |
| Snapshot | `GET /actuator/lofi/{hash}` | `GET /lofi/{hash}` |
| Diff | `GET /actuator/lofi/diff?base=X&head=Y` | `GET /lofi/diff?base=X&head=Y` |

### Snapshot response

```json
{
  "commitHash": "a3f9c1",
  "deployedAt": "2024-11-01T09:00:00Z",
  "metrics": [
    {
      "className": "com.example.OrderService",
      "methodName": "createOrder",
      "elapsedMs": 14.23,
      "recordedAt": "2024-11-01T09:01:23Z"
    }
  ]
}
```

### Diff response

```json
{
  "baseCommit": "a3f9c1",
  "headCommit": "d82e04",
  "diffs": [
    {
      "signature": "com.example.OrderService.createOrder()",
      "baseMs": 14.23,
      "headMs": 91.00,
      "deltaMs": 76.77,
      "regressed": true
    }
  ]
}
```

> A method is flagged as `regressed: true` when `(headMs - baseMs) / baseMs` exceeds the regression threshold (default: `0.2` = 20%).  
> Configurable via `lofi.regression-threshold` (Actuator mode) or `LOFI_BACKEND_REGRESSION_THRESHOLD` (Backend mode).

---

## How It Works

**Actuator mode** — lofi uses Spring AOP to automatically instrument method calls on the following bean types: `@Service`, `@Component`, `@Repository`, `@Controller`, `@RestController`

The following are automatically excluded to avoid double-counting or proxy conflicts:
- Spring framework internals (`org.springframework.*`)
- Jakarta Servlet filters and Spring MVC interceptors
- AspectJ aspects (`@Aspect`)
- JDK dynamic proxies — Spring Data JPA repositories appear as `jdk.proxy2.$Proxy*` in nested-proxy chains, so they are skipped; their execution time is already captured through the enclosing service call
- User-defined package patterns via [`lofi.exclude-packages`](#actuator-mode-applicationyml) — typically self-monitoring / ops endpoints

**Backend mode** — the OpenTelemetry Java Agent instruments the JVM at the bytecode level. Spans are exported to `lofi-otelcol`, which extracts span duration and commit hash (`deployment.commit.hash` resource attribute) and forwards them to `lofi-backend`. Optional [`exclude_packages`](#exporter-side-exclusion-collector-config) on the exporter side drops unwanted spans before ingestion.

In both modes, collected data is stored locally in SQLite. No data leaves your environment.

---

## Security

### Backend mode: `/lofi/ingest` endpoint

`POST /lofi/ingest` requires an **API key** sent in the `X-Lofi-Api-Key` header. `lofi-backend` refuses to start without one.

**Setup**

```bash
# 1. Generate a secret (any string works; 32 hex chars recommended)
export LOFI_API_KEY=$(openssl rand -hex 32)

# 2. lofi-backend reads it from application.yml: api-key: ${LOFI_API_KEY:}
#    (or set lofi.backend.api-key directly)

# 3. lofi-otelcol reads the same env var via collector-config.yaml: api_key: ${env:LOFI_API_KEY}
```

**Behavior**

| Condition | Response |
|-----------|----------|
| `lofi.backend.api-key` not set at startup | Application fails to start with `IllegalStateException` |
| `X-Lofi-Api-Key` header missing on `POST /lofi/ingest` | `401 Unauthorized` |
| Header present but value mismatched | `401 Unauthorized` (constant-time comparison) |
| Read endpoints (`GET /lofi`, `/lofi/{hash}`, `/lofi/diff`) | No authentication — these are intended for CLI/dashboard consumption |

In typical deployments, additionally run `lofi-backend` on a private network. The API key prevents accidental cross-tenant pollution; network isolation prevents targeted attacks.

### Migrating from 0.3.x

Versions ≤ 0.3.2 had no authentication on `/lofi/ingest`. To upgrade:

1. Set `LOFI_API_KEY` (or `lofi.backend.api-key`) on the backend
2. Set the same value on every `lofi-otelcol` instance (`api_key` config field)
3. Restart both — no data migration required

---

## Persisting the SQLite Database

### Actuator mode

lofi stores metrics in `~/.lofi/metrics.db` inside the container. Mount a volume to survive restarts.

**Docker Compose**

```yaml
services:
  app:
    volumes:
      - lofi-data:/root/.lofi

volumes:
  lofi-data:
```

### Backend mode

`lofi-backend` stores metrics at the path configured by `LOFI_BACKEND_DB_PATH` (default: `/data/metrics.db`). The provided `docker-compose.yml` mounts a named volume automatically.

### Kubernetes

Mount a `PersistentVolumeClaim` at `/root/.lofi` (actuator mode) or `/data` (backend mode) so the database survives pod restarts.

> **Note:** In multi-pod environments, each pod writes to its own volume.  
> Cross-pod metric aggregation is not yet supported — see [Limitations](#limitations).

---

## Configuration

### Actuator mode (`application.yml`)

```yaml
lofi:
  commit-hash: ${GIT_COMMIT_HASH:unknown}
  store-type: sqlite              # sqlite (default) or in-memory
  regression-threshold: 0.2       # threshold for regression detection (default: 0.2 = 20%)
  retention-commits: 50           # number of recent deploys to retain (default: 50)
  exclude-packages:               # package patterns to skip from instrumentation (default: empty)
    - com.acme.api.controller.admin.*   # "pkg.*"  — matches pkg and any sub-package (boundary-respecting)
    - com.acme.adaptor.ops              # "pkg"    — legacy startsWith match
  buffer:
    flush-threshold: 100          # number of metrics to batch before flushing (default: 100)
    flush-delay-ms: 5000          # periodic flush interval in ms (default: 5000)
    queue-capacity: 1000          # max buffer queue capacity (default: 1000)
```

> **`exclude-packages`** is useful for self-monitoring / ops endpoints whose classes would otherwise inflate metric counts on every dashboard refresh and drown out real application signal. Prefer the `pkg.*` form — it respects package boundaries (`com.acme.ops` and its sub-packages match, but `com.acme.ops2` does not). The bare-prefix form remains available for legacy use.

### Backend mode (environment variables)

| Variable | Default | Description |
|---|---|---|
| `LOFI_BACKEND_DB_PATH` | `/data/metrics.db` | SQLite database path |
| `LOFI_BACKEND_REGRESSION_THRESHOLD` | `0.2` | Regression detection threshold (20%) |
| `LOFI_BACKEND_RETENTION_COMMITS` | `50` | Number of recent deploys to retain |

#### Exporter-side exclusion (Collector config)

The `lofi` OpenTelemetry exporter in `lofi-otelcol` accepts an `exclude_packages`
setting that mirrors the Actuator-mode `lofi.exclude-packages` property. Spans
whose parsed `className` matches any pattern are dropped **before** being sent
to `lofi-backend`, so self-monitoring / ops endpoints don't inflate metric
counts in Backend mode either.

```yaml
# collector-config.yaml
exporters:
  lofi:
    backend_url: http://localhost:9292
    api_key: ${env:LOFI_API_KEY}
    exclude_packages:
      - com.acme.api.controller.admin.*   # "pkg.*" — matches pkg and sub-packages
      - com.acme.adaptor.ops              # "pkg"   — legacy startsWith match
```

---

## Open Source vs Dashboard

| Feature | Open Source | Dashboard (coming soon) |
|---------|-------------|--------------------------|
| Method-level latency collection | ✓ | ✓ |
| CLI diff viewer | ✓ | ✓ |
| Deploy history | Local only | ✓ |
| Team sharing | — | ✓ |
| Regression alerts | — | ✓ |

---

## Limitations

- Currently works in single-pod environments only. Multi-pod support is available in the dashboard plan.
- Requires Spring Boot 3.x and Java 17 or higher.

---

lofi is in early development. Feedback is welcome — open an issue or reach out.
