# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
| 0.1.3   | 2026-04-13 | Exclude Spring internal classes from AOP instrumentation |
| 0.1.2   | 2026-04-13 | Fix JPA conflict caused by lofi SQLite DataSource |
| 0.1.1   | 2026-04-13 | Fix DataSource auto-configuration ordering       |
| 0.1.0   | 2026-04-13 | First functional release                         |
| 0.0.1   | 2026-04-10 | Initial skeleton                                 |

---

## Upgrade Guide

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

[Unreleased]: https://github.com/closeup1202/lofi/compare/v0.1.3...HEAD
[0.1.3]: https://github.com/closeup1202/lofi/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/closeup1202/lofi/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/closeup1202/lofi/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/closeup1202/lofi/compare/v0.0.1...v0.1.0
[0.0.1]: https://github.com/closeup1202/lofi/releases/tag/v0.0.1
