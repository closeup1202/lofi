# lofi GitHub Actions examples

Drop-in workflows that block PRs introducing latency regressions. Pick the one that matches your deployment mode.

| File | Use when |
|------|----------|
| [`lofi-regression-check-backend.yml`](./lofi-regression-check-backend.yml) | You run a standalone `lofi-backend` server collecting metrics from `lofi-otelcol`. |
| [`lofi-regression-check-actuator.yml`](./lofi-regression-check-actuator.yml) | Your Spring Boot app embeds `lofi-spring-boot-starter` and exposes `/actuator/lofi`. |

## How to use

1. Copy the file you want into `.github/workflows/` of your repo.
2. Configure the required secret (Settings → Secrets and variables → Actions):
   - Backend mode: `LOFI_BACKEND_URL` (e.g. `https://lofi.internal.example.com`)
   - Actuator mode: `STAGING_URL` (URL of a deployed instance with the PR's HEAD commit)
3. Adjust thresholds at the bottom of the file:
   - `--threshold-rate 0.2` — fail if any method is ≥ 20% slower than base
   - `--threshold-ms 50` — alternative absolute threshold (mutually exclusive with `--threshold-rate`)
   - `--min-calls 30` — ignore methods with too few samples in either deploy
   - `--stat p95` — compare p95 latency (or `avg`, `p99`)

## How it works

```
PR opened ─► GitHub Actions runs lofi-regression-check
              │
              ▼
            git merge-base origin/main HEAD  →  base commit
            git rev-parse HEAD                →  head commit
              │
              ▼
            lofi check <base>..<head> --url ... --threshold-rate 0.2
              │
              ├─ no method exceeds threshold  →  exit 0  →  ✅ PR check passes
              └─ at least one method exceeds  →  exit 1  →  ❌ PR check fails
```

The check is **purely a query** — it does not write metrics. Your production or staging
deploys must already have ingested data for both commits before the check runs.

## Common adjustments

**Run only on labeled PRs:** add `if: contains(github.event.pull_request.labels.*.name, 'perf-check')` to the job.

**Surface the diff in PR comments:** pipe the markdown output to `gh pr comment`:

```yaml
      - name: Comment diff on PR
        if: always()
        run: |
          OUTPUT=$(lofi diff ${{ steps.commits.outputs.base }}..${{ steps.commits.outputs.head }} \
            --url "${{ secrets.LOFI_BACKEND_URL }}" --format md --stat p95 || true)
          gh pr comment ${{ github.event.pull_request.number }} --body "$OUTPUT"
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

**Pin the lofi-cli version** instead of `npm install -g`:
```yaml
      - run: npm install -g @closeup1202/lofi-cli@0.4.0
```
