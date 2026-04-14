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

## Getting Started

### 1. Add the dependency

**Gradle**

```groovy
implementation 'io.github.closeup1202:lofi-spring-boot-starter:0.1.7'
```

**Maven**

```xml
<dependency>
  <groupId>io.github.closeup1202</groupId>
  <artifactId>lofi-spring-boot-starter</artifactId>
  <version>0.1.7</version>
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

> **If your application uses Spring Security**, the actuator endpoints are blocked by default.
> Choose one of the following approaches.

**Option A — Management port separation (recommended)**

Isolate actuator on a separate internal port so it is never reachable from the public network.
No changes to your Security configuration are needed.

```yaml
management:
  server:
    port: 9090
  endpoints:
    web:
      exposure:
        include: lofi, lofiDiff
```

Point the CLI at the internal port:

```bash
lofi diff a3f9c1..d82e04 --url http://localhost:9090
```

**Option B — Permit only the lofi paths**

If port separation is not an option, allow only the lofi endpoints explicitly.

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/lofi/**", "/actuator/lofiDiff").permitAll()
        .anyRequest().authenticated()
    );
    return http.build();
}
```

> Avoid `permitAll()` on the entire `/actuator/**` path — endpoints such as
> `/actuator/env` and `/actuator/heapdump` can leak sensitive information.

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

## Checking via Actuator Endpoints

If you prefer raw JSON or want to integrate with your own tooling, you can query the actuator endpoints directly.

### GET /actuator/lofi/{commitHash}

Returns all raw metrics collected for a given deploy.

```bash
curl http://localhost:8080/actuator/lofi/a3f9c1
```

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

### GET /actuator/lofiDiff?base={baseCommit}&head={headCommit}

Computes a method-level latency diff between two deploys and flags regressions.

```bash
curl "http://localhost:8080/actuator/lofiDiff?base=a3f9c1&head=d82e04"
```

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
    },
    {
      "signature": "com.example.UserService.findById()",
      "baseMs": 3.10,
      "headMs": 3.20,
      "deltaMs": 0.10,
      "regressed": false
    }
  ]
}
```

> A method is flagged as `regressed: true` when `(headMs - baseMs) / baseMs` exceeds `lofi.regression-threshold` (default: `0.2` = 20%).

---

## How It Works

lofi uses Spring AOP to automatically instrument method calls on the following bean types:
`@Service`, `@Component`, `@Repository`, `@Controller`, `@RestController`

The following are automatically excluded to avoid double-counting or proxy conflicts:

- Spring framework internals (`org.springframework.*`)
- Jakarta Servlet filters and Spring MVC interceptors
- AspectJ aspects (`@Aspect`)
- JDK dynamic proxies — Spring Data JPA repositories appear as `jdk.proxy2.$Proxy*` in nested-proxy chains, so they are skipped; their execution time is already captured through the enclosing service call

Deploy boundaries are detected from the `GIT_COMMIT_HASH` environment variable at application startup.  
Collected data is stored as a SQLite file at `~/.lofi/metrics.db`.  
All data is processed locally. No data leaves your machine unless you opt into a dashboard.

---

## Persisting the SQLite Database

lofi stores all metrics in `~/.lofi/metrics.db` inside the container.  
Without a volume mount, the file is lost on every container restart, making deploy-to-deploy diff comparison impossible.

### Docker

```bash
docker run \
  -e GIT_COMMIT_HASH=$(git rev-parse --short HEAD) \
  -v $HOME/.lofi:/root/.lofi \
  my-app
```

### Docker Compose

```yaml
services:
  app:
    build:
      context: .
      args:
        GIT_COMMIT_HASH: ${GIT_COMMIT_HASH}
    environment:
      - GIT_COMMIT_HASH=${GIT_COMMIT_HASH}
    volumes:
      - lofi-data:/root/.lofi

volumes:
  lofi-data:
```

> Using a named volume (`lofi-data`) keeps the database across container recreations.  
> If you prefer a host-mounted path, replace with `- $HOME/.lofi:/root/.lofi`.

### Kubernetes

Mount a `PersistentVolumeClaim` at `/root/.lofi` so the database survives pod restarts.

```yaml
spec:
  containers:
    - name: app
      env:
        - name: GIT_COMMIT_HASH
          value: "a3f9c1"
      volumeMounts:
        - name: lofi-storage
          mountPath: /root/.lofi
  volumes:
    - name: lofi-storage
      persistentVolumeClaim:
        claimName: lofi-pvc
```

> **Note:** In multi-pod environments, each pod writes to its own volume.  
> Cross-pod metric aggregation is not yet supported — see [Limitations](#limitations).

### Local development (non-containerized)

No action needed. lofi writes to `~/.lofi/metrics.db` on the host directly and the file persists across restarts.

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

---

lofi is in early development. Feedback is welcome — open an issue or reach out.
