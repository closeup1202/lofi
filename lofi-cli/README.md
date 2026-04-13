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
─────────────────────────────────────────────────────────────────────────
  Method                                        Before   After    Delta
─────────────────────────────────────────────────────────────────────────
  OrderService.createOrder()                     14ms  →  91ms   +77ms  ▲
  PaymentService.validate()                      22ms  →  58ms   +36ms  ▲
  UserService.findById()                          3ms  →   3ms      —
─────────────────────────────────────────────────────────────────────────
  2 regression(s) detected
```

Regressed methods are highlighted in red.

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
  Deployed at: 2026-04-13T10:00:00Z
  Metrics collected: 120

  Method                                           Avg   Calls
────────────────────────────────────────────────────────────
  OrderService.createOrder()                       14ms    45
  UserService.findById()                            3ms    75
```

---

## Options

| Option | Default | Description |
|--------|---------|-------------|
| `-u, --url <url>` | `http://localhost:8080` | Actuator base URL of the target application |

---

## How It Works

`lofi diff` calls `/actuator/lofiDiff?base=<commit>&head=<commit>` on your Spring Boot app and renders the response as a formatted table.

`lofi snapshot` calls `/actuator/lofi/<commitHash>` and shows average latency per method for that deploy.

Both commands require the target app to be running and reachable at the specified URL.

---

## Quick Start

**1. Add the library to your Spring Boot app**

```groovy
implementation 'io.github.closeup1202:lofi-spring-boot-starter:0.1.3'
```

**2. Expose actuator endpoints**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: lofi, lofiDiff
```

**3. Run your app with a commit hash**

```bash
export GIT_COMMIT_HASH=$(git rev-parse --short HEAD)
./gradlew bootRun
```

**4. After deploying a second version, compare**

```bash
lofi diff <first-commit>..<second-commit> --url http://localhost:8080
```

---

## License

MIT
