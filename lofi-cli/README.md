# lofi-cli

Terminal CLI for [lofi](https://github.com/closeup1202/lofi) — see which methods slowed down between deploys.

![npm](https://img.shields.io/npm/v/@closeup1202/lofi-cli?style=flat-square)
![Node.js](https://img.shields.io/badge/node-%3E%3D18-brightgreen?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)

---

## Requirements

- Node.js 18 or higher
- A running lofi target — either:
  - A Spring Boot app with [lofi-spring-boot-starter](https://github.com/closeup1202/lofi) (actuator mode), or
  - A running [lofi-backend](https://github.com/closeup1202/lofi) instance (backend mode)

---

## Installation

```bash
npm install -g @closeup1202/lofi-cli
```

Verify the installation:

```bash
lofi --version
lofi --help
```

---

## Commands

### `lofi diff` — compare performance between two deploys

```bash
lofi diff [<base>..<head>] [--url <url>]
```

```bash
# Pass commit range directly
lofi diff a3f9c1..d82e04 --url http://localhost:8080

# Or omit the range for an interactive selector
lofi diff --url http://localhost:8080

# Output as JSON or markdown
lofi diff a3f9c1..d82e04 --format json
lofi diff a3f9c1..d82e04 --format markdown
```

**Output:**

```
Deploy Diff  a3f9c1 → d82e04
──────────────────────────────────────────────────────────────────────────────────
  Method                                        Before      After       Delta
──────────────────────────────────────────────────────────────────────────────────
  OrderService.createOrder()                   14.23ms  →  91.00ms   +76.77ms  ▲
  PaymentService.validate()                    22.10ms  →  58.40ms   +36.30ms  ▲
  UserService.findById()                        3.05ms  →   3.12ms    +0.07ms  —
──────────────────────────────────────────────────────────────────────────────────
  2 regression(s) detected
```

- Regressed methods are highlighted in **red**
- A method is flagged as regressed when its latency increases by more than the configured threshold (default: 20%)

---

### `lofi check` — fail if latency regression exceeds threshold (CI mode)

```bash
lofi check <base>..<head> [--threshold-ms <ms>] [--threshold-rate <rate>] [--url <url>]
```

```bash
lofi check a3f9c1..d82e04 --threshold-ms 50 --url http://staging.myapp.com
lofi check a3f9c1..d82e04 --threshold-rate 0.2 --url http://staging.myapp.com
```

**Output (pass):**

```
✓ All within threshold
```

**Output (fail):**

```
✗ 2 method(s) exceeded threshold
  OrderService.createOrder()  14.23ms → 91.00ms  (+76.77ms, +539.5%)
  PaymentService.validate()   22.10ms → 58.40ms  (+36.30ms, +164.3%)
```

Exits with code `1` on failure — GitHub Actions (and most CI systems) treat this as a failed step automatically.

Options `--threshold-ms` and `--threshold-rate` are mutually exclusive.

#### Prerequisites

`lofi check` requires **both commits to be already deployed with metrics collected**. It is not a pre-deploy gate — the intended pattern is a **staging → production gate**.

```
Deploy v1 to staging → generate traffic → metrics collected
Deploy v2 to staging → generate traffic → metrics collected
lofi check v1..v2    ← compare here
Deploy to production
```

If no metrics are found for a commit, `lofi check` prints a warning and exits with code `0` instead of failing:

```
⚠ No data found for commit: d82e04 — skipping check
```

This means teams that are gradually adopting a staging environment can add `lofi check` to their pipeline without risk of breaking it.

#### `--format` option

Use `--format` to control output format:

| Format | Description |
|--------|-------------|
| `table` | Default. Human-readable colored output |
| `json` | Machine-readable. Useful for piping or parsing in CI |
| `markdown` | GitHub-flavored markdown. Useful for posting to PR comments |

**`--format json` output:**

```json
{
  "passed": false,
  "threshold": { "ms": 50 },
  "exceeded": [
    {
      "signature": "com.example.OrderService.createOrder()",
      "baseMs": 14.23,
      "headMs": 91.00,
      "deltaMs": 76.77,
      "changeRate": 5.3954
    }
  ]
}
```

**`--format markdown` output:**

```markdown
## Latency Check: ✗ 1 method(s) exceeded threshold

| Method | Before | After | Delta | Change |
|--------|--------|-------|-------|--------|
| OrderService.createOrder() | 14.23ms | 91.00ms | +76.77ms | +539.5% |

> Threshold: 50ms
```

#### GitHub Actions example

```yaml
- name: Check latency regression
  env:
    BASE: ${{ github.event.pull_request.base.sha }}
    HEAD: ${{ github.event.pull_request.head.sha }}
  run: lofi check $BASE..$HEAD --threshold-ms 50 --url https://staging.myapp.com
```

Post results as a PR comment using `--format markdown`:

```yaml
- name: Check latency regression
  env:
    BASE: ${{ github.event.pull_request.base.sha }}
    HEAD: ${{ github.event.pull_request.head.sha }}
  run: |
    lofi check $BASE..$HEAD --threshold-ms 50 --format markdown \
      --url https://staging.myapp.com > report.md || true
    gh pr comment ${{ github.event.pull_request.number }} --body-file report.md

- name: Fail on regression
  env:
    BASE: ${{ github.event.pull_request.base.sha }}
    HEAD: ${{ github.event.pull_request.head.sha }}
  run: lofi check $BASE..$HEAD --threshold-ms 50 --format json \
    --url https://staging.myapp.com
```

For push events, use `github.event.before` and `github.sha` instead:

```yaml
- name: Check latency regression
  env:
    BASE: ${{ github.event.before }}
    HEAD: ${{ github.sha }}
  run: lofi check $BASE..$HEAD --threshold-ms 50 --url https://staging.myapp.com
```

---

### `lofi snapshot` — view metrics for a specific deploy

```bash
lofi snapshot [<commitHash>] [--url <url>]
```

```bash
# Pass commit hash directly
lofi snapshot a3f9c1 --url http://localhost:8080

# Or omit for an interactive selector
lofi snapshot --url http://localhost:8080
```

**Output:**

```
Snapshot  a3f9c1
────────────────────────────────────────────────────────────
  Deployed at: 2026-04-14T10:00:00Z
  Metrics collected: 120

  Method                                           Avg   Calls
────────────────────────────────────────────────────────────
  OrderService.createOrder()                    14.23ms    45
  UserService.findById()                         3.05ms    75
```

---

## Options

| Option | Default | Description |
|--------|---------|-------------|
| `--url <url>` | `http://localhost:8080` | Target URL (actuator or lofi-backend) |
| `--threshold-ms <ms>` | | Absolute latency threshold in ms (`check`, `diff`) |
| `--threshold-rate <rate>` | | Relative threshold as a decimal — `0.2` = 20% (`check`, `diff`) |
| `--format <format>` | `table` | Output format: `table`, `json`, `markdown` (`check`, `diff`) |
| `--version` | | Print the CLI version |
| `--help` | | Display help |

---

## Auto-Detection

The CLI automatically detects whether the target is a `lofi-backend` instance or a Spring Boot actuator endpoint by probing `GET /lofi/commits` on startup. No extra flags are needed.

```bash
# Actuator mode (Spring Boot app on 8080)
lofi diff a3f9c1..d82e04 --url http://localhost:8080

# Backend mode (lofi-backend on 9292)
lofi diff a3f9c1..d82e04 --url http://localhost:9292
```

Both work identically — the CLI adapts the API paths internally.

---

## Typical Workflow

### Actuator mode

```
Deploy v1 (commit: a3f9c1)
  └─ GIT_COMMIT_HASH=a3f9c1 → metrics collected via Spring AOP

Deploy v2 (commit: d82e04)
  └─ GIT_COMMIT_HASH=d82e04 → metrics collected via Spring AOP

After deploy:
  lofi diff a3f9c1..d82e04 --url http://localhost:8080
```

**1. Deploy v1 with a commit hash**

```bash
export GIT_COMMIT_HASH=$(git rev-parse --short HEAD)
./gradlew bootRun
# [LO-FI] Monitoring active — commit: a3f9c1 | store: sqlite | regression-threshold: 0.2
```

**2. Send some traffic to your app**

**3. Check the snapshot to confirm metrics are recorded**

```bash
lofi snapshot a3f9c1 --url http://localhost:8080
```

**4. Deploy v2 and repeat, then compare**

```bash
lofi diff a3f9c1..d82e04 --url http://localhost:8080
```

### Backend mode (OTel pipeline)

```
Your App (OTel Agent)
    │  OTLP
    ▼
lofi-otelcol → lofi-backend (:9292)
    │
    ▼  lofi-cli
```

```bash
# Start the pipeline
docker compose up

# Run your app with the OTel agent
OTEL_RESOURCE_ATTRIBUTES=deployment.commit.hash=$(git rev-parse --short HEAD) \
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318 \
java -javaagent:opentelemetry-javaagent.jar -jar your-app.jar

# Compare deploys
lofi diff a3f9c1..d82e04 --url http://localhost:9292
```

---

## Quick Start (actuator mode)

**1. Add the library to your Spring Boot app**

```groovy
implementation 'io.github.closeup1202:lofi-spring-boot-starter:0.2.0'
```

**2. Expose the actuator endpoint**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: lofi
```

**3. Set the commit hash and run**

```bash
export GIT_COMMIT_HASH=$(git rev-parse --short HEAD)
./gradlew bootRun
```

**4. After a second deploy, compare**

```bash
lofi diff <first-commit>..<second-commit> --url http://localhost:8080
```

---

## Spring Security

If your app uses Spring Security, the actuator endpoints may return `403 Forbidden`.

**Option A — Management port separation (recommended)**

```yaml
management:
  server:
    port: 9090
```

```bash
lofi diff a3f9c1..d82e04 --url http://localhost:9090
```

**Option B — Permit only the lofi paths**

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/lofi/**").permitAll()
    .anyRequest().authenticated()
);
```

> Do not use `permitAll()` on the entire `/actuator/**` path — it exposes sensitive endpoints like `/actuator/env` and `/actuator/heapdump`.

---

## Troubleshooting

### Connection failed

```
Cannot connect to http://localhost:8080
```

- Make sure your app (or lofi-backend) is running and reachable
- Verify the URL and port with `--url`
- If using Docker or a remote server, replace `localhost` with the correct host

### No data found

```
No data found — no metrics recorded for commit: a3f9c1
```

- **Actuator mode**: confirm `GIT_COMMIT_HASH` was set when the app started (look for `[LO-FI] Monitoring active` in the logs) and that the app received traffic after startup
- **Backend mode**: confirm `deployment.commit.hash` was set as an OTel resource attribute and that `lofi-otelcol` is running and connected to `lofi-backend`

### 403 Forbidden

The actuator endpoints are blocked by Spring Security. See [Spring Security](#spring-security) above.

---

## How It Works

**Actuator mode**

- `lofi diff` → `GET /actuator/lofi/diff?base=<commit>&head=<commit>`
- `lofi snapshot` → `GET /actuator/lofi/<commitHash>`

**Backend mode**

- `lofi diff` → `GET /lofi/diff?base=<commit>&head=<commit>`
- `lofi snapshot` → `GET /lofi/snapshot/<commitHash>`

Latency values are in **milliseconds** (e.g. `14.23ms`). Both actuator and backend convert from internal nanoseconds on the server side — the CLI receives ready-to-display values.

---

## License

MIT
