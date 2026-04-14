# lofi-cli

Terminal CLI for [lofi](https://github.com/closeup1202/lofi) — see which methods slowed down between deploys.

![npm](https://img.shields.io/npm/v/@closeup1202/lofi-cli?style=flat-square)
![Node.js](https://img.shields.io/badge/node-%3E%3D18-brightgreen?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)

---

## Requirements

- Node.js 18 or higher
- A running Spring Boot app with [lofi-spring-boot-starter](https://github.com/closeup1202/lofi) configured
- Actuator endpoints exposed (`lofi`, `lofiDiff`)

---

## Installation

```bash
npm install -g @closeup1202/lofi-cli
```

Verify the installation:

```bash
lofi --version  # 0.1.8
lofi --help
```

---

## Commands

### `lofi diff` — compare performance between two deploys

```bash
lofi diff <base>..<head> [--url <actuator-url>]
```

```bash
# Example
lofi diff a3f9c1..d82e04 --url http://localhost:8080
```

**Output:**

```
Deploy Diff  a3f9c1 → d82e04
──────────────────────────────────────────────────────────────────────────────
  Method                                        Before      After       Delta
──────────────────────────────────────────────────────────────────────────────
  OrderService.createOrder()                   14.23ms  →  91.00ms   +76.77ms  ▲
  PaymentService.validate()                    22.10ms  →  58.40ms   +36.30ms  ▲
  UserService.findById()                        3.05ms  →   3.12ms    +0.07ms  —
──────────────────────────────────────────────────────────────────────────────
  2 regression(s) detected
```

- Regressed methods are highlighted in **red**
- A method is flagged as regressed when its latency increases by more than the configured threshold (default: 20%)

---

### `lofi snapshot` — view metrics for a specific deploy

```bash
lofi snapshot <commitHash> [--url <actuator-url>]
```

```bash
# Example
lofi snapshot a3f9c1 --url http://localhost:8080
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
| `-u, --url <url>` | `http://localhost:8080` | Actuator base URL of the target application |
| `-V, --version` | | Print the CLI version |
| `-h, --help` | | Display help for a command |

---

## Typical Workflow

```
Deploy v1 (commit: a3f9c1)
  └─ GIT_COMMIT_HASH=a3f9c1 → metrics collected while app runs

Deploy v2 (commit: d82e04)
  └─ GIT_COMMIT_HASH=d82e04 → metrics collected while app runs

After deploy:
  lofi diff a3f9c1..d82e04 → regression report
```

**Step by step:**

**1. Deploy v1 with a commit hash**

```bash
export GIT_COMMIT_HASH=$(git rev-parse --short HEAD)
./gradlew bootRun
# [LO-FI] Monitoring active — commit: a3f9c1 | store: sqlite | regression-threshold: 0.2
```

**2. Send some traffic to your app**

Let the app collect metrics by handling real or test requests.

**3. Check the snapshot to confirm metrics are being recorded**

```bash
lofi snapshot a3f9c1 --url http://localhost:8080
```

**4. Deploy v2 and repeat**

```bash
export GIT_COMMIT_HASH=$(git rev-parse --short HEAD)
./gradlew bootRun
```

**5. Compare the two deploys**

```bash
lofi diff a3f9c1..d82e04 --url http://localhost:8080
```

---

## Quick Start (from scratch)

**1. Add the library to your Spring Boot app**

```groovy
implementation 'io.github.closeup1202:lofi-spring-boot-starter:0.1.8'
```

**2. Expose actuator endpoints**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: lofi, lofiDiff
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
    .requestMatchers("/actuator/lofi/**", "/actuator/lofiDiff").permitAll()
    .anyRequest().authenticated()
);
```

> Do not use `permitAll()` on the entire `/actuator/**` path — it exposes sensitive endpoints like `/actuator/env` and `/actuator/heapdump`.

---

## Troubleshooting

### Connection failed

```
Connection failed — connect ECONNREFUSED http://localhost:8080
Check the actuator URL with the --url option
```

- Make sure your Spring Boot app is running
- Verify the URL and port with `--url`
- If using Docker or a remote server, replace `localhost` with the correct host

### No data found

```
No data found — no metrics recorded for commit: a3f9c1
Verify the commit hash is correct and that metrics were collected for that deploy
```

- Confirm `GIT_COMMIT_HASH` was set when the app started (check for `[LO-FI] Monitoring active — commit: a3f9c1` in the app logs)
- Make sure the app received traffic after startup so metrics were collected
- Check that `lofi.store-type` is set to `sqlite` (default) — `in-memory` metrics are lost on restart

### 403 Forbidden

The actuator endpoints are blocked by Spring Security. See the [Spring Security](#spring-security) section above.

### Empty diff result

- Check that both commit hashes exist in the database: run `lofi snapshot <hash>` for each
- If both snapshots show 0 metrics, the app may not have received any instrumented requests during that deploy

---

## How It Works

`lofi diff` calls `GET /actuator/lofiDiff?base=<commit>&head=<commit>` and renders the response as a formatted table.

`lofi snapshot` calls `GET /actuator/lofi/<commitHash>` and shows average latency per method.

Latency values are in **milliseconds** (e.g. `14.23ms`). Internally, lofi measures in nanoseconds for precision and converts on the way out.

Both commands require the target app to be running and reachable at the specified URL.

---

## License

MIT
