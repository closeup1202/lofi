# lofi

See which methods slowed down since your last deploy — instantly.

lofi is a lightweight observability library for Spring Boot teams.  
Add one dependency, deploy — and method-level latency diffs are generated automatically.

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?style=flat-square)
![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)

---

## The Problem

p99 latency spiked after your last deploy.  
To find which method caused it? Open Grafana, check Jaeger, dig through logs, read the commit diff — and connect the dots yourself, every time.

lofi links deploy events to code and shows you the diff directly.

---

## What It Looks Like

```bash
$ lofi diff a3f9c1..d82e04

Deploy Diff  main@a3f9c1 → main@d82e04
────────────────────────────────────────────────────────
  Method                               Before    After    Delta
────────────────────────────────────────────────────────
  OrderService.createOrder()           14ms  →  91ms   +77ms  ▲
  PaymentClient.validate()             22ms  →  58ms   +36ms  ▲
  UserService.findById()                3ms  →   3ms      —
  ProductService.getStock()             8ms  →   9ms      —
────────────────────────────────────────────────────────
  2 regressions detected
```

---

## Getting Started

### 1. Add the dependency

**Gradle**

```groovy
implementation 'io.github.closeup1202:lofi-spring-boot-starter:0.1.0'
```

**Maven**

```xml
<dependency>
  <groupId>io.github.closeup1202</groupId>
  <artifactId>lofi-spring-boot-starter</artifactId>
  <version>0.1.0</version>
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

**Kubernetes**

```yaml
env:
  - name: GIT_COMMIT_HASH
    value: "a3f9c1"
```

### 3. Expose actuator endpoints

```yaml
management:
  endpoints:
    web:
      exposure:
        include: lofi, lofiDiff
```

### 4. Install lofi-cli

```bash
npm install -g @closeup1202/lofi-cli
```

---

## Usage

### diff — compare performance between two deploys

```bash
lofi diff <base>..<head> --url http://localhost:8080
```

```bash
# example
lofi diff a3f9c1..d82e04 --url http://localhost:8080
```

### snapshot — view metrics for a specific deploy

```bash
lofi snapshot <commitHash> --url http://localhost:8080
```

```bash
# example
lofi snapshot a3f9c1 --url http://localhost:8080
```

---

## How It Works

lofi uses Spring AOP to automatically instrument method calls on `@Service`, `@Component`, and `@Repository` beans.  
Deploy boundaries are detected from the `GIT_COMMIT_HASH` environment variable at application startup.  
Collected data is stored as a SQLite file at `~/.lofi/metrics.db`.  
All data is processed locally. No data leaves your machine unless you opt into a dashboard.

---

## Configuration

You can tune collection behavior in `application.yml`.

```yaml
lofi:
  commit-hash: ${GIT_COMMIT_HASH:unknown}
  store-type: sqlite              # sqlite (default) or in-memory
  regression-threshold: 0.2       # threshold for regression detection (default: 0.2 = 20%)
  buffer:
    flush-threshold: 100          # number of metrics to batch before flushing (default: 100)
    flush-delay-ms: 5000          # periodic flush interval in ms (default: 5000)
    queue-capacity: 1000          # max buffer queue capacity (default: 1000)
```

### Store types

| store-type | Description |
|------------|-------------|
| `sqlite` | Persisted to `~/.lofi/metrics.db` (default) |
| `in-memory` | In-memory only, data lost on restart. Recommended for test/dev environments |

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
- Only `@Service`, `@Component`, and `@Repository` beans are instrumented automatically.

---

lofi is in early development. Feedback is welcome — open an issue or reach out.
