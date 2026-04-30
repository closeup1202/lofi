# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

---

## [0.4.3] - 2026-04-30

### Added
- **`GET /lofi/version` endpoint** (`lofi-backend`) — returns `{appName, appVersion, retentionCommits, regressionThreshold}` so clients (CLI, exporter, ops dashboards) can diagnose policy mismatches between backend and clients without inspecting the deployed binary. Unauthenticated, mirroring the other read endpoints. Backed by Spring Boot's `BuildProperties` (build-info.properties is now generated via `springBoot { buildInfo() }` in `lofi-backend/build.gradle`).
- **PR comment + optional notification steps in both example workflows** (`examples/github-actions/`) — the regression-check workflow now upserts a markdown latency-diff comment on the PR via `actions/github-script` (find by `<!-- lofi-regression-check -->` marker, update if present, create otherwise) so re-pushes do not pile up duplicate comments. Required permission: `pull-requests: write`. Two pre-written notification steps (Slack / generic webhook) are included **commented out**; users uncomment one block and add the matching repository secret. The notification fires only when the diff step exited non-zero (regression beyond threshold), not on every PR.
- **`lofi-cli` once-per-day update notification** (`@closeup1202/lofi-cli`) — at most once every 24 hours the CLI checks `https://registry.npmjs.org/@closeup1202/lofi-cli/latest` in the background and, if a newer version is available, prints a single line at the end of the command output (after the actual results, never before): `lofi: update available 0.4.2 → 0.4.x  ·  npm install -g @closeup1202/lofi-cli  (set LOFI_NO_UPDATE_CHECK=1 to silence)`. Cache lives at `${XDG_CACHE_HOME:-$HOME/.cache}/lofi-cli/version-check.json`. **Auto-disabled** when stdout is not a TTY (piped / redirected output), when `CI` env is set (any GitHub Actions / GitLab CI / etc. run), and when `--format json` / `--format markdown` is in use (so machine-readable output never gets a stray banner line). **Manual opt-out**: pass `--no-update-check`, set `LOFI_NO_UPDATE_CHECK=1`, or set the cross-CLI `NO_UPDATE_NOTIFIER=1`. The HTTP fetch has a 3-second timeout and any failure is silently absorbed (with a stamped cache entry so the CLI doesn't retry-storm when offline).

### Changed
- **`lofi-cli` adaptive unit formatting** (`@closeup1202/lofi-cli` 0.4.2) — diff and snapshot tables, plus markdown PR-comment output, now render large millisecond values with the most appropriate unit: `<1s` stays `ms`, `1s ≤ x < 60s` → `s`, `≥ 60s` → `Xm Ys` (e.g. `2m 9s`). A 45-second regression now reads `45.06s` instead of `45057.72ms`. Numeric `--format json` output is unchanged (raw `*Ms` fields).
- **`lofi-cli` dynamic Method-column width** — diff and snapshot tables size the Method column to the longest signature in the dataset (with a `'Method'` floor) instead of a hard-coded 41/44 chars. A signature longer than the previous fixed pad no longer pushes its row out of alignment with the rest of the table.
- **`lofi-cli` `--format md` accepted as alias for `markdown`** — the existing example workflows passed `--format md`, which the CLI previously rejected with `Invalid format`. The alias is now resolved at parse time and produces the same output as `--format markdown`. Help text updated to `--format <table|json|markdown>` (with the `md` alias mentioned).

### Fixed
- **All Spring Boot module test suites now actually execute** (`build.gradle`) — `useJUnitPlatform()` was only configured on `lofi-integration-test`. Every other Java module (`lofi-core`, `lofi-collector`, `lofi-actuator`, `lofi-backend`, `lofi-spring-boot-starter`) had a passing `:test` task that silently discovered zero tests, because Gradle's default test runner doesn't pick up JUnit 5 annotations. Configured at the root `subprojects` level so all 73 existing JUnit 5 tests now run on every CI build.
- **`SqliteMetricStoreTest` schema visibility** (`lofi-collector`) — the test used `jdbc:sqlite::memory:` together with `DriverManagerDataSource`, which opens a fresh connection per request. SQLite's `:memory:` databases are connection-private, so the schema created in `@BeforeEach` was invisible to the connection that ran `saveAll`, causing `no such table: method_metric` errors. The test now creates a per-test SQLite file under `@TempDir`, which is auto-cleaned by JUnit. (Surfaced by the `useJUnitPlatform()` fix above; the test file already existed but had never been executed.)

### Notes
- Library / `lofi-backend` artifacts: **0.4.2 → 0.4.3**.
- `lofi-cli` (`@closeup1202/lofi-cli`) artifact: **0.4.0 → 0.4.2**.
- HTTP wire format unchanged. Custom bean overrides for `LofiController` need to accept the new `BuildProperties` and two `@Value` constructor parameters; the bundled `lofi-backend` autoconfiguration handles this automatically.

---

## [0.4.2] - 2026-04-27

### Added
- **`MethodMetricRequest` ingest DTO** (`lofi-backend`) — `IngestRequest.metrics` is now `List<MethodMetricRequest>` with per-element `@Valid` cascade. Each metric is validated at the API boundary: `className`/`methodName` are `@NotBlank` and length-capped (512 / 256), `elapsedNs` is `@PositiveOrZero`, and `recordedAt` is `@NotNull`. Invalid elements produce `400 Bad Request` with a per-field message (e.g. `metrics[3].className: must not be blank`) instead of being persisted as-is. The `lofi-core` domain record stays dependency-free.
- **`/lofi/ingest` request now wrapped in a single SQLite transaction** — `SqliteWritableMetricStore.ingest` runs the batch insert and any retention eviction inside one `TransactionTemplate.executeWithoutResult` boundary, so an ingest causes one fsync instead of one per row.
- **`SqliteMetricStore.saveAll` now wrapped in a single SQLite transaction** (`lofi-collector`) — same rationale: each `MetricBuffer` flush is one fsync instead of N. Auto-configured `lofiTransactionTemplate` bean (`lofi-spring-boot-starter`) bound to the lofi-private `DriverManagerDataSource`, so it does not participate in the application's `@Transactional` boundaries.
- **`BackendDatabaseInitializer` now purges retention on startup** — mirrors the collector-side `LofiDatabaseInitializer` so backend instances do not carry over commits beyond `lofi.backend.retention-commits` between restarts.

### Changed
- **SQLite JDBC URL now sets `journal_mode=WAL&busy_timeout=5000`** in both `lofi-spring-boot-starter` and `lofi-backend`. WAL allows readers to proceed concurrently with the single SQLite writer; `busy_timeout` (ms) tells SQLite to wait/retry on a contended write lock instead of immediately throwing `SQLITE_BUSY` (e.g. `lofi-flush` daemon racing with an overflow flush from a request thread, or two ingest requests landing simultaneously).
- **Backend retention eviction is now triggered only on new commits** — `SqliteWritableMetricStore.ingest` probes via `SELECT NOT EXISTS(...)` (uses `idx_commit_hash`, O(log n)) and runs `evictOldCommits()` only when a commit hash is observed for the first time. Repeated ingests for an existing commit no longer cause a full-table `DELETE NOT IN (subquery)` scan on the hot path. Also dropped the redundant `SELECT DISTINCT ... GROUP BY commit_hash` in the eviction SQL.
- **`MetricBuffer` flush coalescing** — every flush path (overflow, threshold, scheduled) now goes through the same `flushing` CAS gate, so producers, the `lofi-flush` daemon, and overflow handling cannot stack concurrent `metricStore.saveAll` calls. The CAS gate already protected the threshold path; this extends it to the previously unguarded overflow and scheduled paths.
- **`MetricBuffer` overflow eviction now reports drops accurately** — when the queue stays full after a flush attempt and the eviction `offer()` retry also fails (rare contention case), the metric is now logged as `dropping metric: <signature>` instead of the previous misleading `oldest metric evicted to make room` (which fired regardless of whether the second `offer` had succeeded, so a silently-dropped metric looked like a successful eviction in logs).
- **`InMemoryMetricStore.saveAll` is now `synchronized` end-to-end** — registration and `addAll` were on different locks, opening a narrow window where retention rollover for a concurrent `saveAll` could evict the just-registered commit between `registerCommitIfAbsent` returning and `addAll` running. Now consistent with the other operations on this store.

### Removed
- **`WritableMetricStore.save(MethodMetric)`** — single-metric port method had no production callers (`MetricBuffer` only ever calls `saveAll`). Implementations (`SqliteMetricStore`, `InMemoryMetricStore`) and the default `saveAll` fallback are removed; both stores now batch directly. **Breaking** for downstream code that supplied its own `WritableMetricStore` bean and overrode `save(MethodMetric)` — implement `saveAll(List<MethodMetric>)` instead.

### Constructor signature changes (breaking for custom bean overrides)
- `SqliteMetricStore(JdbcTemplate, DeployContext, TransactionTemplate)` — added `TransactionTemplate`. The starter wires this automatically via the new `lofiTransactionTemplate` bean.
- `SqliteWritableMetricStore(JdbcTemplate, TransactionTemplate, int retentionCommits)` — added `TransactionTemplate`.
- `BackendDatabaseInitializer(JdbcTemplate, String dbPath, int retentionCommits)` — added `retentionCommits` for the new startup purge.

### Notes
- HTTP wire format is unchanged — `MethodMetricRequest` has the same field names and types as `MethodMetric` (`className`, `methodName`, `elapsedNs`, `recordedAt`), so existing `lofi-otel-exporter` and other ingest clients require no changes.
- `lofi-cli` (`@closeup1202/lofi-cli`) version remains at **0.4.0**: read-only CLI surface (`commits` / `snapshot` / `diff`) is unaffected by these backend changes.

---

## [0.4.1] - 2026-04-25

### Added
- **`lofi.exclude-packages` configuration property** — list of fully-qualified package patterns whose beans are skipped by `LofiInterceptor`. Two forms are supported:
  - `com.acme.ops.*` — matches `com.acme.ops` itself and any sub-package, respecting package boundaries (so `com.acme.ops2` is not matched).
  - `com.acme.ops` — legacy prefix match via `startsWith` (may match unrelated packages sharing the prefix; prefer the wildcard form).

  Useful for self-monitoring / ops dashboards whose classes would otherwise inflate metric counts on every refresh and drown out real application signal. Matched against the target bean's class name at interception time (pointcut remains broad, since AspectJ pointcut strings cannot be parameterised from `@ConfigurationProperties`). Defaults to an empty list (no additional exclusions beyond the built-in Spring-framework / proxy filters). Example:
  ```yaml
  lofi:
    exclude-packages:
      - com.acme.api.controller.admin.*
      - com.acme.adaptor.ops.*
  ```

- **`exclude_packages` exporter setting for Backend mode** — the `lofi` OpenTelemetry exporter in `lofi-otelcol` now accepts a matching `exclude_packages` list with the same pattern semantics (`pkg.*` / legacy `pkg` / bare `*`). Spans whose parsed `className` matches any pattern are dropped before being sent to `lofi-backend`, keeping Backend-mode parity with the Actuator-mode property. Matchers are pre-compiled once at exporter construction so the span hot path stays allocation-free. Example:
  ```yaml
  exporters:
    lofi:
      backend_url: http://localhost:9292
      api_key: ${env:LOFI_API_KEY}
      exclude_packages:
        - com.acme.api.controller.admin.*
        - com.acme.adaptor.ops.*
  ```

### Changed
- **`LofiInterceptor` constructor now takes `(MetricBuffer, List<String>)`** — downstream callers overriding the bean must pass the new `excludePackages` list (empty list to preserve previous behavior). All bundled autoconfiguration is wired automatically.

---

## [0.4.0] - 2026-04-23

### Security (breaking)
- **`/lofi/ingest` now requires API-key authentication** — `lofi-backend` refuses to start unless `lofi.backend.api-key` (or `LOFI_API_KEY` env var) is set. Every `POST /lofi/ingest` request must include the `X-Lofi-Api-Key` header; missing or mismatched keys return `401 Unauthorized` (constant-time comparison). Read endpoints remain unauthenticated. **Upgrade**: set `LOFI_API_KEY` on the backend and pass the same value to every `lofi-otelcol` instance via the new `api_key` exporter config field. See README "Backend mode: `/lofi/ingest` endpoint" and "Migrating from 0.3.x".
- **`lofi-otel-exporter` `api_key` config required** — the Go exporter now refuses to start without `api_key` set in `collector-config.yaml`.

### Added
- **`lofi-backend` Spring Actuator `/actuator/health` endpoint** — `spring-boot-starter-actuator` added; only the `health` endpoint is exposed (`management.endpoints.web.exposure.include=health`), with `show-details=never` to avoid leaking infra info. The auth interceptor does not cover this path, so it is freely available for liveness/readiness probes.
- **GitHub Actions workflow examples** — `examples/github-actions/` contains drop-in workflows for both `lofi-backend` and `lofi-actuator` modes that block PRs introducing latency regressions. See README "Drop-in GitHub Actions workflows".
- **CLI test suite (Vitest)** — 24 unit tests covering `LofiClient` mode resolution / URL switching and pure render helpers (`shortSignature`, `getStatMs`, `applyFilters`, `isRegressed`, `exceedsThreshold`, `thresholdLabel`). Wired into CI as a separate job.

### Changed
- **`IngestRequest` validation hardened** — `commitHash` must match `^[a-fA-F0-9]{7,40}$` (git short/full hash); `metrics` capped at 10,000 entries per request. Invalid requests return `400 Bad Request`.
- **SQLite JDBC driver version unified** — `sqliteJdbcVersion = 3.49.1.0` extracted to root `build.gradle` `ext` block and referenced by `lofi-collector`, `lofi-backend`, `lofi-integration-test`. Eliminates the previous 3.45.3.0/3.49.1.0 split.

### Fixed
- **Docker Compose healthcheck** — `lofi-backend` healthcheck now hits the new `/actuator/health` endpoint instead of the non-existent `/lofi/commits` path.
- **`lofi-backend` Docker image now builds on linux/arm64** — base image switched from `eclipse-temurin:17-jre-alpine` (no arm64 manifest) to `eclipse-temurin:17-jre-jammy`, which ships multi-arch (amd64 + arm64). Apple Silicon and arm64 server users no longer need `--platform=linux/amd64` overrides. `wget` is installed in the runtime image for the existing healthcheck command.

---

## [0.3.2] - 2026-04-20

### Changed
- **Snapshot endpoint now returns aggregated stats instead of raw metrics** — `GET /actuator/lofi/{hash}` and `GET /lofi/{hash}` previously returned a flat list of every individual measurement. They now return per-method `avg`, `p95`, `p99`, and `count` (all in milliseconds), keeping response size proportional to the number of methods rather than traffic volume.
- **Percentile formula unified** — in-memory `DeploySnapshot.statsByMethod()` now uses the same index formula as the SQL window-function query (`(n * 95 + 99) / 100`), eliminating a subtle discrepancy between the two code paths.
- **`InMemoryMetricStore.saveAll()` explicit override** — batch writes now use `CopyOnWriteArrayList.addAll()` instead of delegating to the default loop, making the intent clear.

### Added
- **`ReadableMetricStore.deployedAt(String commitHash)`** — new default method returning the earliest recorded timestamp for a commit. SQLite-backed stores override it with a lightweight `MIN(recorded_at)` query; the snapshot endpoint uses this instead of loading raw rows.
- **`MethodStatsView`** — new view record exposing `avgMs`, `p95Ms`, `p99Ms`, `count` for a single method in the snapshot response.
- **CI workflow** (`.github/workflows/ci.yaml`) — runs build and tests on every pull request and push to `main`.
- **`SqliteMetricStoreTest`** — unit tests for the SQL window-function query covering avg, single-sample percentiles, 100-sample P95/P99, multiple methods, isolation between commits.
- **`LofiBackendIntegrationTest`** — end-to-end integration test for backend mode covering ingest, commit listing, snapshot, diff, and regression detection.

### Security
- **Backend `/lofi/ingest` security guidance added to README** — documents the risk of exposing the ingest endpoint to untrusted networks and recommends keeping `lofi-backend` on an internal network or adding reverse-proxy authentication.

---

## [0.3.1] - 2026-04-20

### Fixed
- **`spring-boot-configuration-processor` added to `lofi-spring-boot-starter`** — the annotation processor was missing, so IDE property completion (`lofi.*` keys in `application.yml`) and `spring-configuration-metadata.json` were not generated at compile time. Adding the processor restores full IDE auto-complete for all `lofi.*` configuration properties.

---

## [0.3.0] - 2026-04-20

### Removed
- **`MetricStore` interface removed (breaking)** — the unified `MetricStore extends ReadableMetricStore, WritableMetricStore` interface has been deleted. Actuator-mode implementations (`SqliteMetricStore`, `InMemoryMetricStore`) now implement `ReadableMetricStore` and `WritableMetricStore` directly. See [upgrade guide](#from-024-to-030).
- **`lofi-sample` module removed** — the sample application module has been deleted; the integration test module (`lofi-integration-test`) covers the same scenarios.

### Changed
- **`LofiAutoConfiguration` bean wiring updated** — `MetricBuffer` now receives `WritableMetricStore`; `DiffService` and `LofiEndpoint` now receive `ReadableMetricStore`. Custom bean overrides must target the appropriate split interface.
- **`MetricBuffer` constructor parameter type changed** — `MetricStore` → `WritableMetricStore` (breaking if constructed directly).
- **`MetricBuffer.destroy()` shutdown sequence hardened** — `scheduler.awaitTermination(1s)` is now called between `shutdown()` and the final `flush()`, eliminating a theoretical race condition where an in-flight scheduled flush could overlap the destroy-time flush. If termination times out, `shutdownNow()` is called before flushing.

### Performance
- **Duplicate `STATS_BY_METHOD_SQL` consolidated** — extracted to `LofiSqlQueries.STATS_BY_METHOD` in `lofi-core`; `SqliteMetricStore` and `SqliteReadableMetricStore` both reference the single constant.

### Documentation
- **`lofi check` exit code table added** — conditions for exit `1`, exit `0`, and the missing-metrics case are now explicitly documented
- **`retention-commits` added to Actuator mode configuration reference**
- **`LOFI_BACKEND_REGRESSION_THRESHOLD` and `LOFI_BACKEND_RETENTION_COMMITS` added to Backend mode environment variable table**
- **`regressed: true` judgment documents both mode config keys**

---

## [0.2.4] - 2026-04-19

### Fixed
- **`LofiEndpoint` snapshot returns 404 when commit not found** — previously returned an empty metrics array with HTTP 200; now returns `{"error": "..."}` with HTTP 404, matching the behaviour of the backend mode (`LofiQueryService`)
- **`UnsupportedOperationException` removed from store implementations** — `ingest()` has been split into a dedicated `IngestableStore` port; `SqliteMetricStore` and `InMemoryMetricStore` no longer declare an unimplemented `ingest()` override, and `SqliteWritableMetricStore` no longer declares an unimplemented `save()` override

### Changed
- **`@EnableScheduling` removed from `LofiAutoConfiguration`** — `MetricBuffer` now owns a single self-managed daemon thread (`lofi-flush`) via `ScheduledExecutorService`, so lofi no longer activates Spring's scheduling infrastructure in the host application. On graceful shutdown, `MetricBuffer.destroy()` stops the scheduler and performs a final flush so no buffered metrics are lost
- **Diff engine uses DB-level percentile aggregation** — `DiffServiceImpl` now calls `ReadableMetricStore.statsByMethod()` instead of `snapshot()`, avoiding loading all raw rows into the JVM heap. SQLite-backed stores (`SqliteMetricStore`, `SqliteReadableMetricStore`) implement this with a CTE + `ROW_NUMBER()` window-function query that computes avg / P95 / P99 entirely in the database

### Performance
- **Composite index added** — both DB initializers now create `idx_commit_method_elapsed (commit_hash, class_name, method_name, elapsed_ns)`, providing a covering index for the `statsByMethod` window-function query

---

## [0.2.3] - 2026-04-15

### Changed
- **Backend API paths unified with actuator structure** — `GET /lofi/commits` → `GET /lofi`, `GET /lofi/snapshot/{hash}` → `GET /lofi/{hash}`; paths now mirror the actuator prefix pattern, reducing asymmetry between modes

### CLI (0.2.9)
- Auto-detection probe updated to `GET /lofi` (was `GET /lofi/commits`)
- Backend snapshot URL updated to `GET /lofi/{hash}` (was `GET /lofi/snapshot/{hash}`)
- Backend commits URL updated to `GET /lofi` (was `GET /lofi/commits`)

---

## [0.2.2] - 2026-04-15

### Changed
- **`regressed` flag now reflects the selected stat** — when using `--stat p95` or `--stat p99`, regression coloring and count in `lofi diff` output are computed against the selected percentile using the server's configured `regressionThreshold`, not the server-side avg-based flag
- **`DiffResult` / `DiffResultView`** now include `regressionThreshold` so the CLI can apply consistent regression logic client-side regardless of which stat is selected

### Added
- **lofi-backend retention policy** — `lofi.backend.retention-commits` (default `50`) automatically evicts the oldest commits after each ingest, keeping SQLite from growing unboundedly

### CLI (0.2.8)
- `lofi diff` regression coloring now consistent with `--stat` selection

---

## [0.2.1] - 2026-04-15

### Added
- **P95 / P99 latency percentiles in diff response** — `MethodDiffView` now includes `baseP95Ms`, `headP95Ms`, `baseP99Ms`, `headP99Ms` alongside the existing average fields, giving consumers richer latency data without additional API calls
- **Call counts in diff response** — `MethodDiffView` now includes `baseCount` and `headCount` (invocation count per deploy), enabling clients to assess statistical confidence of each latency comparison
- **`MethodStats` domain record** — encapsulates `avgNs`, `p95Ns`, `p99Ns`, and `count` per method; computed by `DeploySnapshot.statsByMethod()` in a single sorted pass

### Changed
- `DeploySnapshot.averageByMethod()` replaced by `statsByMethod()` — returns `Map<String, MethodStats>` with full percentile stats; `DiffServiceImpl` now uses this for all diff calculations
- `MethodDiff` record updated with six new fields: `baseP95Ns`, `headP95Ns`, `baseP99Ns`, `headP99Ns`, `baseCount`, `headCount`

### CLI (0.2.6)
- **`--stat avg|p95|p99`** option added to `lofi diff` and `lofi check` — selects which latency stat to display and compare against thresholds (default: `avg`)
- **`--min-calls <n>`** option added to `lofi diff` and `lofi check` — suppresses methods where both deploys have fewer than n invocations, filtering out statistically unreliable results; new or removed methods (present in only one deploy) are always shown
- **`Calls` column** added to `lofi diff` table output — shows `baseCount→headCount` per method so users can judge data reliability at a glance
- **No-threshold warning in `lofi check`** — running `lofi check` without `--threshold-ms` or `--threshold-rate` now prints a usage guide and exits 0 instead of silently passing all methods
- **Timezone abbreviation in commit selector** — timestamps now include `timeZoneName: 'short'` (e.g. `KST`, `UTC`, `PDT`) so the timezone is always visible

---

## [0.2.0] - 2026-04-14

### Added
- **`lofi-backend`** — standalone Spring Boot server that accepts OTLP-derived metrics and exposes them via REST API
  - `POST /lofi/ingest` — ingests span data forwarded from `lofi-otelcol`
  - `GET /lofi/commits` — list of all recorded deploys
  - `GET /lofi/snapshot/{commitHash}` — per-method metrics for a specific deploy
  - `GET /lofi/diff?base=X&head=Y` — method-level latency diff between two deploys
- **`lofi-otelcol`** — custom OpenTelemetry Collector binary built with OCB
  - Receives OTLP traces (gRPC `:4317`, HTTP `:4318`) and forwards metrics to `lofi-backend`
  - Cross-platform binaries available: `linux-amd64`, `linux-arm64`, `darwin-amd64`, `darwin-arm64`
- **`install.sh`** — one-line installer for `lofi-otelcol` binary (detects OS and arch automatically)
  ```bash
  curl -fsSL https://raw.githubusercontent.com/closeup1202/lofi/main/install.sh | sh
  ```
- **`docker-compose.yml`** — runs `lofi-backend` + `lofi-otelcol` together with a single command
  ```bash
  docker compose up
  ```
- **GitHub Actions** — cross-platform `lofi-otelcol` binaries built and uploaded to GitHub Releases on every `v*` tag push
- **Shared view layer in `lofi-core`** — `DeploySnapshotView`, `DiffResultView`, `MethodMetricView`, `MethodDiffView` centralize ns→ms conversion on the server side; both actuator and backend use the same view classes

### Changed
- **Actuator diff endpoint consolidated** — `LofiDiffEndpoint` (`@WebEndpoint(id="lofiDiff")`) merged into `LofiEndpoint`
  - Before: `GET /actuator/lofiDiff?base=X&head=Y`
  - After: `GET /actuator/lofi/diff?base=X&head=Y`
  - `management.endpoints.web.exposure.include: lofi` is now sufficient — `lofiDiff` no longer needed
- **CLI `--url` / `-U` unification** — the CLI now auto-detects whether the target is `lofi-backend` or `lofi-actuator` by probing `GET /lofi/commits`; no separate mode flag is needed
- **ns→ms conversion moved to server side** — actuator and backend both return `elapsedMs`, `baseMs`, `headMs`, `deltaMs` as `double`; the CLI no longer performs any unit conversion
- **Spring Boot upgraded 3.3.0 → 3.5.12**

### Security
- **CVE-2026-22733** — upgraded Spring Boot from `3.3.0` to `3.5.12` to address the Spring Security authentication bypass vulnerability affecting CloudFoundry Actuator endpoint paths. The 3.3.x release line does not carry a patch; upgrading to the `3.5.12` release is the recommended fix.

---

## [0.1.8] - 2026-04-14

### Added
- **Commit list endpoint** — `GET /actuator/lofi` now returns a summary list of all recorded deploys (commitHash, deployedAt, metricCount), ordered by deploy time descending
- **Interactive commit selection in CLI** — `lofi diff` and `lofi snapshot` can now be run without arguments; the CLI fetches the commit list and presents an arrow-key selector instead of requiring the hash to be typed manually

### Fixed
- **Schema migration on startup** — `LofiDatabaseInitializer` now detects the old `elapsed_ms` column and automatically renames it to `elapsed_ns` (converting values ms → ns), so existing databases from ≤ 0.1.5 are migrated in place without data loss
- **CLI table separator width** — diff separator adjusted to 84 characters and snapshot separator to 61 characters to align with actual content row widths; diff header `Before`/`After` column padding corrected to match data rows

### Changed
- `MetricStore` interface gains a `listCommits()` method — custom implementations must now override it
- `lofi diff [range]` and `lofi snapshot [commitHash]` arguments are now optional

---

## [0.1.7] - 2026-04-14

### Added
- **Startup log** — lofi now prints an INFO log on application startup showing the active commit hash, store type, and regression threshold, making it easy to confirm the configuration at a glance
  ```
  [LO-FI] Monitoring active — commit: a3f9c1 | store: sqlite | regression-threshold: 0.2
  ```

### Fixed
- **LofiDiffEndpoint parameter validation** — `GET /actuator/lofiDiff` now returns a clear `IllegalArgumentException` with usage instructions when `base` or `head` is blank, instead of silently returning an empty result
- **DeployContext null check cleanup** — replaced `StringUtils.hasLength()` with `commitHash != null && !commitHash.isBlank()`, removing the unnecessary Spring utility dependency in the core record
- **README How It Works accuracy** — `@Controller` and `@RestController` added to the instrumented bean list; automatic exclusions (Spring internals, Servlet filters, MVC interceptors, AspectJ aspects, JDK dynamic proxies) now explicitly documented
- **CLI version** — `lofi --version` now correctly reports `0.1.7` (was stuck at `0.1.5`)

### Changed
- **Debug logging in LofiInterceptor** — successful metric recordings now emit a `DEBUG`-level log (`[lofi] Recorded {class}.{method}() — {ns}ns`). Activate with `logging.level.io.github.closeup1202.lofi=DEBUG`

---

## [0.1.6] - 2026-04-14

### Added
- **SQLite volume mount guide** in README — Docker, Docker Compose, and Kubernetes examples for persisting `~/.lofi/metrics.db` across container restarts
- **Spring Security integration guide** in README — management port separation (recommended) and per-path `permitAll` as fallback, with a warning against opening the entire `/actuator/**` path
- **Actuator endpoint reference** in README — `GET /actuator/lofi/{commitHash}` and `GET /actuator/lofiDiff` with request/response examples
- **Docker Compose commit hash injection** example added to README

### Changed
- Metric elapsed time is now stored internally as **nanoseconds** (`elapsedNs`) via `System.nanoTime()` for higher precision and immunity to system clock adjustments
- Actuator HTTP responses expose latency in **milliseconds** (`elapsedMs`, `baseMs`, `headMs`, `deltaMs`) via a dedicated view layer (`MethodMetricView`, `DeploySnapshotView`, `MethodDiffView`, `DiffResultView`) — internal domain models remain in nanoseconds
- `LofiDiffEndpoint` changed from `@Endpoint` to `@WebEndpoint` for explicit HTTP-only semantics and improved API documentation compatibility
- `spring-boot-starter-actuator` dependency in `lofi-actuator` promoted from `implementation` to `api` — consumers of `lofi-spring-boot-starter` now get actuator on the compile classpath automatically, enabling IDE property completion for `management.*`
- Settings validation migrated from manual `IllegalArgumentException` in record compact constructors to **JSR-303 annotations** (`@Pattern`, `@DecimalMin`, `@DecimalMax`, `@Min`) with `@Validated` — all constraint violations are now reported at once on startup instead of stopping at the first failure

### Fixed
- **JDK dynamic proxy filtering** — `LofiInterceptor` now skips targets where `Proxy.isProxyClass()` is `true` (e.g. Spring Data JPA repositories in nested-proxy chains), eliminating meaningless `jdk.proxy2.$Proxy173`-style class names and duplicate measurements from metrics
- **MetricBuffer overflow** — when the queue is still full after an emergency flush, the oldest buffered metric is now evicted (`queue.poll()`) to make room for the incoming one, ensuring recent measurements are never silently dropped

---

## [0.1.5] - 2026-04-13

### Fixed
- Excluded `jakarta.servlet.Filter` subclasses (e.g. `JwtAuthenticationFilter`) from AOP instrumentation to prevent conflicts with Spring Security filter chains
- Excluded `HandlerInterceptor` subclasses from AOP instrumentation
- Excluded `@Aspect` annotated classes from AOP instrumentation to prevent proxy conflicts
- Added `jakarta.servlet:jakarta.servlet-api` and `spring-webmvc` as `compileOnly` dependencies to support the pointcut exclusions

---

## [0.1.3] - 2026-04-13

### Fixed
- Excluded Spring framework internal classes (`org.springframework.*`) from AOP instrumentation to prevent Spring Boot built-in beans (e.g. `BasicErrorController`) from appearing in metrics

---

## [0.1.2] - 2026-04-13

### Fixed
- Inlined lofi SQLite `DataSource` inside `lofiJdbcTemplate` bean to prevent it from being registered as a `DataSource` candidate, which was causing Spring Boot JPA auto-configuration (`@ConditionalOnSingleCandidate`) to fail when used alongside an application datasource (e.g. PostgreSQL)
- `LofiAutoConfiguration` runs after `DataSourceAutoConfiguration` via `@AutoConfiguration(after = DataSourceAutoConfiguration.class)`

---

## [0.1.0] - 2026-04-13

### Added

#### Core Features
- Multi-module Spring Boot performance monitoring library (`lofi-core`, `lofi-collector`, `lofi-actuator`, `lofi-spring-boot-starter`)
- AOP-based automatic method instrumentation for `@Service`, `@Component`, `@Repository`, `@Controller`, `@RestController` beans
- SQLite-backed persistent metric storage (`~/.lofi/metrics.db`) with per-commit indexing
- In-memory metric store for test and development environments
- Async metric buffering (`MetricBuffer`) with configurable flush threshold and periodic flush interval
- Deploy diff via `/actuator/lofiDiff?base=<commit>&head=<commit>` — regression detection with configurable threshold (default 20%)
- Snapshot query via `/actuator/lofi/{commitHash}` — returns all recorded metrics for a deployment
- `lofi-spring-boot-starter` auto-configuration with zero-code integration
- TypeScript CLI (`lofi diff`, `lofi snapshot`) for terminal-based diff visualization
- DB retention policy — keeps only the last N commits (default 50) on startup

#### Configuration
- `lofi.commit-hash` — current deployment identifier (supports `${GIT_COMMIT_HASH}`)
- `lofi.store-type` — `sqlite` (default) or `in-memory`
- `lofi.regression-threshold` — regression detection ratio (default `0.2`, range `0.0`–`1.0`)
- `lofi.retention-commits` — number of commits to retain (default `50`, min `1`)
- `lofi.buffer.flush-threshold` — metric count before forced flush (default `100`, min `1`)
- `lofi.buffer.flush-delay-ms` — periodic flush interval in ms (default `5000`, min `1`)
- `lofi.buffer.queue-capacity` — max queue size before drop (default `1000`, min `1`)

#### Regression Detection Logic
- Methods only in head (new): `regressed = false`
- Methods with `baseMs = 0` and `headMs > 0`: `regressed = true`
- Methods removed from head: included with `headMs = 0`, `regressed = false`
- Normal case: `regressed = (deltaMs / baseMs) > regressionThreshold`

### Fixed
- Use FQCN (`getName()`) instead of simple class name to prevent signature collisions
- Exception-throwing methods excluded from metrics to prevent latency pollution
- Metric drop warning log added to `MetricBuffer` when queue is full after flush
- `deployedAt` returns `Instant.EPOCH` instead of `Instant.now()` for empty snapshots
- `InMemoryMetricStore` retention applied via insertion-ordered eviction
- Redundant `DISTINCT` removed from retention SQL in `LofiDatabaseInitializer`

### Tests
- Unit tests: `DeployContextTest`, `LofiInterceptorTest`, `MetricBufferTest`, `DiffServiceImplTest`, `LofiPropertiesTest`
- Integration test: full deploy diff scenario with real SQLite DB (`LofiDeployDiffIntegrationTest`)

---

## [0.0.1] - 2026-04-10

### Added
- Initial project skeleton with `lofi-core` domain model
- Basic `MetricStore` and `DiffService` port interfaces
- Proof-of-concept AOP interceptor and SQLite store

---

## Version History

| Version | Date       | Description                                      |
|---------|------------|--------------------------------------------------|
| 0.4.2   | 2026-04-27 | Per-element ingest validation, single-fsync transactional batch flushes (collector + backend), SQLite WAL + busy_timeout, MetricBuffer flush coalescing + accurate drop logging, retention eviction only on new commits + startup purge in backend, removed dead `WritableMetricStore.save(MethodMetric)` |
| 0.4.1   | 2026-04-25 | `lofi.exclude-packages` configuration property + matching `exclude_packages` exporter setting (Backend mode parity) |
| 0.4.0   | 2026-04-23 | API-key auth on /lofi/ingest (breaking), /actuator/health, IngestRequest validation, GitHub Actions examples, CLI Vitest suite, multi-arch Docker image |
| 0.3.2   | 2026-04-20 | Snapshot returns aggregated stats, percentile formula unified, CI workflow, integration tests |
| 0.3.1   | 2026-04-20 | Add spring-boot-configuration-processor for IDE property completion |
| 0.3.0   | 2026-04-20 | Remove MetricStore god interface, MetricBuffer shutdown race fix, SQL deduplication |
| 0.2.4   | 2026-04-19 | 404 on missing snapshot, IngestableStore split, @EnableScheduling removed, DB-level percentile query |
| 0.2.3   | 2026-04-15 | Backend API paths unified with actuator structure |
| 0.2.2   | 2026-04-15 | regressed flag stat consistency, lofi-backend retention policy |
| 0.2.1   | 2026-04-15 | P95/P99 percentiles, call counts in diff, CLI --stat / --min-calls |
| 0.2.0   | 2026-04-14 | lofi-backend, lofi-otelcol, OTel pipeline, Docker, actuator endpoint consolidation, CVE fix |
| 0.1.8   | 2026-04-14 | Commit list endpoint, interactive CLI commit selection, schema migration |
| 0.1.7   | 2026-04-14 | Startup log, parameter validation, debug logging, docs fixes |
| 0.1.6   | 2026-04-14 | Nanosecond precision, JDK proxy fix, JSR-303 validation, docs |
| 0.1.5   | 2026-04-13 | Exclude Servlet filters, HandlerInterceptors, Aspects from AOP |
| 0.1.3   | 2026-04-13 | Exclude Spring internal classes from AOP instrumentation |
| 0.1.2   | 2026-04-13 | Fix JPA conflict caused by lofi SQLite DataSource |
| 0.1.1   | 2026-04-13 | Fix DataSource auto-configuration ordering       |
| 0.1.0   | 2026-04-13 | First functional release                         |
| 0.0.1   | 2026-04-10 | Initial skeleton                                 |

---

## Upgrade Guide

### From 0.3.x to 0.4.0 (breaking)

**`/lofi/ingest` now requires an API key** (Backend mode only — Actuator mode users are unaffected).

1. Generate a secret: `openssl rand -hex 32`
2. Set it on `lofi-backend`: `export LOFI_API_KEY=<secret>` (read by `application.yml: api-key: ${LOFI_API_KEY:}`)
3. Set the same value on every `lofi-otelcol` instance — add `api_key: ${env:LOFI_API_KEY}` under the `lofi:` exporter in `collector-config.yaml`
4. Restart both services. No data migration required.

`lofi-backend` now refuses to start without the key. Read endpoints (`GET /lofi`, `/lofi/{hash}`, `/lofi/diff`) remain unauthenticated. See README "Backend mode: `/lofi/ingest` endpoint" for the full table of behaviors.

### From 0.3.0 to 0.3.1

No API or configuration changes. Update the version and re-deploy.

### From 0.2.4 to 0.3.0

**`MetricStore` interface removed (breaking)**

`MetricStore` no longer exists. If you referenced it directly, update to the appropriate split interface:

| Use case | Replace with |
|---|---|
| Reading metrics (snapshot, diff) | `ReadableMetricStore` |
| Writing metrics (custom AOP collector) | `WritableMetricStore` |

```java
// Before
@Autowired MetricStore metricStore;

// After — inject the interface that matches your use case
@Autowired ReadableMetricStore metricStore;  // read-only access
@Autowired WritableMetricStore metricStore;  // write-only access
```

If you have a custom `MetricStore` implementation, split the `implements` clause:

```java
// Before
public class MyStore implements MetricStore { ... }

// After
public class MyStore implements ReadableMetricStore, WritableMetricStore { ... }
```

**`MetricBuffer` constructor signature changed**

If you construct `MetricBuffer` directly (outside of auto-configuration), update the first parameter type from `MetricStore` to `WritableMetricStore`.

### From 0.2.3 to 0.2.4

No API or configuration changes. Update the version and re-deploy.

- **Custom `MetricStore` implementations**: `ingest()` is no longer part of `WritableMetricStore`. If your custom store implemented it, the method can be safely kept or removed — it is no longer required.
- **Spring scheduling**: if your application was relying on lofi to activate `@EnableScheduling` as a side effect, add `@EnableScheduling` to your own configuration.

### From 0.1.x to 0.2.0

**Actuator endpoint change (breaking)**

The diff endpoint path has changed. Update any scripts or tooling that call it directly.

```yaml
# Before
GET /actuator/lofiDiff?base=X&head=Y

# After
GET /actuator/lofi/diff?base=X&head=Y
```

**`management.endpoints.web.exposure.include`**

Remove `lofiDiff` — only `lofi` is needed now.

```yaml
# Before
management:
  endpoints:
    web:
      exposure:
        include: lofi, lofiDiff

# After
management:
  endpoints:
    web:
      exposure:
        include: lofi
```

**Spring Security config**

```java
// Before
.requestMatchers("/actuator/lofi/**", "/actuator/lofiDiff").permitAll()

// After
.requestMatchers("/actuator/lofi/**").permitAll()
```

**CLI**

The `--url` / `-U` flag is the only flag needed — mode (backend vs actuator) is auto-detected. Existing scripts that pass `--url` continue to work unchanged.

### From 0.1.7 to 0.1.8

- **`MetricStore` implementors**: `listCommits()` is now a required method. If you have a custom `MetricStore` implementation, add the override — returning an empty list is a safe no-op default.
- **Schema migration**: existing `~/.lofi/metrics.db` databases from ≤ 0.1.5 (with `elapsed_ms` column) are migrated automatically on first startup. No manual action needed.
- **CLI**: `lofi diff` and `lofi snapshot` arguments are now optional. Existing scripts that pass arguments directly continue to work unchanged.

### From 0.1.6 to 0.1.7

- **No API changes.** Update the version and re-deploy.

### From 0.1.5 to 0.1.6

- **No API changes** for application code. Update the version and re-deploy.
- The actuator JSON response fields have changed: `elapsedMs` (snapshot) and `baseMs` / `headMs` / `deltaMs` (diff) now carry **millisecond values as `double`** (e.g. `14.23`) instead of integer milliseconds. Update any tooling that parses the raw JSON.

### From 0.1.x to 0.1.2

- No API changes. Update the version and re-deploy.
- If you added a `@Primary` `DataSource` bean as a workaround for the datasource conflict, it can be safely removed.

### From 0.0.x to 0.1.x

- Replace any direct `MetricStore` usage with the auto-configured bean
- Set `lofi.commit-hash` via environment variable `GIT_COMMIT_HASH` in your deployment pipeline
- Expose actuator endpoints: `management.endpoints.web.exposure.include=lofi`

---

## Contributing

When contributing, please update this changelog:

1. Add your changes under `[Unreleased]` section
2. Use appropriate category:
    - **Added** for new features
    - **Changed** for changes in existing functionality
    - **Deprecated** for soon-to-be removed features
    - **Removed** for now removed features
    - **Fixed** for any bug fixes
    - **Security** for vulnerability fixes
3. Include issue/PR references where applicable

---

[Unreleased]: https://github.com/closeup1202/lofi/compare/v0.4.2...HEAD
[0.4.2]: https://github.com/closeup1202/lofi/compare/v0.4.1...v0.4.2
[0.4.1]: https://github.com/closeup1202/lofi/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/closeup1202/lofi/compare/v0.3.2...v0.4.0
[0.3.2]: https://github.com/closeup1202/lofi/compare/v0.3.1...v0.3.2
[0.3.1]: https://github.com/closeup1202/lofi/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/closeup1202/lofi/compare/v0.2.4...v0.3.0
[0.2.4]: https://github.com/closeup1202/lofi/compare/v0.2.3...v0.2.4
[0.2.3]: https://github.com/closeup1202/lofi/compare/v0.2.2...v0.2.3
[0.2.2]: https://github.com/closeup1202/lofi/compare/v0.2.1...v0.2.2
[0.2.1]: https://github.com/closeup1202/lofi/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/closeup1202/lofi/compare/v0.1.8...v0.2.0
[0.1.8]: https://github.com/closeup1202/lofi/compare/v0.1.7...v0.1.8
[0.1.7]: https://github.com/closeup1202/lofi/compare/v0.1.6...v0.1.7
[0.1.6]: https://github.com/closeup1202/lofi/compare/v0.1.5...v0.1.6
[0.1.5]: https://github.com/closeup1202/lofi/compare/v0.1.4...v0.1.5
[0.1.3]: https://github.com/closeup1202/lofi/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/closeup1202/lofi/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/closeup1202/lofi/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/closeup1202/lofi/compare/v0.0.1...v0.1.0
[0.0.1]: https://github.com/closeup1202/lofi/releases/tag/v0.0.1
