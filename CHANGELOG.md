# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

### From 0.1.7 to 0.1.8

- **`MetricStore` implementors**: `listCommits()` is now a required method. If you have a custom `MetricStore` implementation, add the override — returning an empty list is a safe no-op default.
- **Schema migration**: existing `~/.lofi/metrics.db` databases from ≤ 0.1.5 (with `elapsed_ms` column) are migrated automatically on first startup. No manual action needed.
- **CLI**: `lofi diff` and `lofi snapshot` arguments are now optional. Existing scripts that pass arguments directly continue to work unchanged.

### From 0.1.6 to 0.1.7

- **No API changes.** Update the version and re-deploy.
- A new INFO log is printed on startup — no action needed, but you can suppress it by setting `logging.level.io.github.closeup1202.lofi.autoconfigure=WARN`.
- `GET /actuator/lofiDiff` without `base` or `head` now returns a 500 with a descriptive message instead of an empty result.

### From 0.1.5 to 0.1.6

- **No API changes** for application code. Update the version and re-deploy.
- The actuator JSON response fields have changed: `elapsedMs` (snapshot) and `baseMs` / `headMs` / `deltaMs` (diff) now carry **millisecond values as `double`** (e.g. `14.23`) instead of integer milliseconds. Update any tooling that parses the raw JSON.
- `spring-boot-starter-actuator` is now a transitive compile dependency — you no longer need to declare it explicitly in your app if you were only adding it for lofi.
- If you use Spring Data JPA, `jdk.proxy2.$Proxy*` entries will disappear from metrics automatically.

### From 0.1.x to 0.1.3

- No API changes. Update the version and re-deploy.
- Spring Boot internal beans (e.g. `BasicErrorController`) will no longer appear in metrics.

### From 0.1.x to 0.1.2

- No API changes. Update the version and re-deploy.
- If you added a `@Primary` `DataSource` bean as a workaround for the datasource conflict, it can be safely removed.

### From 0.0.x to 0.1.x

- Replace any direct `MetricStore` usage with the auto-configured bean
- Set `lofi.commit-hash` via environment variable `GIT_COMMIT_HASH` in your deployment pipeline
- Expose actuator endpoints: `management.endpoints.web.exposure.include=lofi,lofiDiff`

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

[Unreleased]: https://github.com/closeup1202/lofi/compare/v0.1.8...HEAD
[0.1.8]: https://github.com/closeup1202/lofi/compare/v0.1.7...v0.1.8
[0.1.7]: https://github.com/closeup1202/lofi/compare/v0.1.6...v0.1.7
[0.1.6]: https://github.com/closeup1202/lofi/compare/v0.1.5...v0.1.6
[0.1.5]: https://github.com/closeup1202/lofi/compare/v0.1.4...v0.1.5
[0.1.3]: https://github.com/closeup1202/lofi/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/closeup1202/lofi/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/closeup1202/lofi/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/closeup1202/lofi/compare/v0.0.1...v0.1.0
[0.0.1]: https://github.com/closeup1202/lofi/releases/tag/v0.0.1
