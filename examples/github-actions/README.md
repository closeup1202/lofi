# lofi GitHub Actions examples

Drop-in workflows that block PRs introducing latency regressions and post a markdown
diff comment on the PR. Pick the one that matches your deployment mode.

| File | Use when |
|------|----------|
| [`lofi-regression-check-backend.yml`](./lofi-regression-check-backend.yml) | You run a standalone `lofi-backend` server collecting metrics from `lofi-otelcol`. |
| [`lofi-regression-check-actuator.yml`](./lofi-regression-check-actuator.yml) | Your Spring Boot app embeds `lofi-spring-boot-starter` and exposes `/actuator/lofi`. |

## How to use

1. Copy the file you want into `.github/workflows/` of your repo.
2. Configure the required secret (Settings → Secrets and variables → Actions):
   - Backend mode: `LOFI_BACKEND_URL` (e.g. `https://lofi.internal.example.com`)
   - Actuator mode: `STAGING_URL` (URL of a deployed instance with the PR's HEAD commit)
3. Adjust thresholds inside the **Run lofi diff** step:
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
            lofi diff <base>..<head> --format markdown   ← captures full diff to /tmp/lofi-diff.md
              │
              ├─ no method exceeds threshold  →  diff exits 0
              └─ at least one method exceeds  →  diff exits non-zero (gated by continue-on-error)
              │
              ▼
            Upsert PR comment via actions/github-script
              │   • finds an existing comment with the marker `<!-- lofi-regression-check -->`
              │   • updates it in place if found, otherwise creates a new one
              │   • re-pushes to the PR don't pile up duplicate comments
              ▼
            Final fail step  →  exit 1 if diff exited non-zero  →  ❌ PR check fails
                            └► otherwise exit 0                  →  ✅ PR check passes
```

The check is **purely a query** — it does not write metrics. Your production or staging
deploys must already have ingested data for both commits before the check runs.

## ⚠️ Traffic prerequisite (most common pitfall)

`lofi diff` compares **observed** latency between two commits. If the HEAD commit
has been deployed but has not yet served any requests, there are zero metric rows
for it, every method gets filtered out by `--min-calls`, and the diff reports
"no regressions" — a **false negative** that lets a real regression merge.

You need to make sure HEAD has traffic before this job queries it. Pick one:

- **Synthetic load** — run k6 / JMeter / Locust against staging after deploy.
  Aim for at least `--min-calls × number_of_methods` total calls covering the
  paths you care about. The actuator example includes a commented-out k6 step.
- **e2e / smoke suite** — if you already run end-to-end tests against staging
  pre-merge, schedule them before the lofi check and let their traffic supply
  the samples.
- **Mirrored production traffic** — Envoy / NGINX traffic shadowing into
  staging means HEAD gets real traffic for free; just leave a soak window
  (long enough to clear `--min-calls`) before the check runs.

In **actuator mode**, after traffic stops, allow ~10–30s for `MetricBuffer` to
flush in-process metrics to SQLite before querying `/actuator/lofi`. The
buffer flushes on a schedule and on a size threshold, but the last partial
batch only lands on the next tick.

In **backend mode**, the equivalent delay is the OTel exporter's batch interval
plus network round-trip — usually a few seconds.

If `--min-calls` is too aggressive for a small staging environment, lower it
(e.g. `--min-calls 10`) — but be aware that smaller samples mean noisier p95
and a higher false-positive rate.

## What the PR comment looks like

```
## Deploy Diff: `a3f9c1` → `d82e04`

> Stat: p95  ·  Threshold: 20%  ·  Min calls: 30

| Method                                | Before (p95) | After (p95) | Delta       | Calls   |
|---------------------------------------|--------------|-------------|-------------|---------|
| OrderService.createOrder()            | 14.00ms      | 91.00ms     | +77.00ms 🔥 | 100→120 |
| PaymentClient.validate()              | 22.00ms      | 58.00ms     | +36.00ms 🔥 |  90→100 |
| UserService.findById()                |  3.00ms      |  3.10ms     |  +0.10ms    | 200→200 |

**2 regression(s) detected** · **2 exceeded threshold 🔥**
```

Numbers are rendered with adaptive units (`ms` / `s` / `Xm Ys`) so a 45-second
regression shows as `45.06s` instead of `45057.72ms`. The CLI reports raw numeric
milliseconds via `--format json`.

## Required job permissions

Both example workflows declare:

```yaml
permissions:
  pull-requests: write
```

This is needed to post or update the diff comment. Without it the comment step
fails with a 403.

## Optional: Slack / Discord / generic webhook notifications

Both workflows include two pre-written notification steps, **commented out by
default**. Uncomment exactly **one** and add the matching repository secret.

**Slack** — create an [incoming webhook](https://api.slack.com/messaging/webhooks)
in your workspace, save the URL as `SLACK_WEBHOOK_URL`:

```yaml
- name: Notify Slack on regression
  if: steps.diff.outcome == 'failure'
  uses: slackapi/slack-github-action@v2
  with:
    webhook: ${{ secrets.SLACK_WEBHOOK_URL }}
    webhook-type: incoming-webhook
    payload: |
      text: "lofi: regression on <${{ github.event.pull_request.html_url }}|PR #${{ github.event.pull_request.number }}> (${{ github.repository }})"
```

**Discord** — Discord channel webhooks accept Slack-shaped payloads when the URL
ends with `/slack`. Append `/slack` to your channel webhook URL and reuse the
Slack block above with `SLACK_WEBHOOK_URL` set to the Discord URL.

**Generic webhook** (Microsoft Teams, custom alert servers, etc.) — POSTs a JSON
body with `text` and `diff` fields. Set `LOFI_NOTIFY_WEBHOOK_URL`:

```yaml
- name: Notify generic webhook on regression
  if: steps.diff.outcome == 'failure'
  env:
    WEBHOOK_URL: ${{ secrets.LOFI_NOTIFY_WEBHOOK_URL }}
    PR_URL: ${{ github.event.pull_request.html_url }}
    PR_NUM: ${{ github.event.pull_request.number }}
    REPO: ${{ github.repository }}
  run: |
    DIFF=$(cat /tmp/lofi-diff.md)
    jq -nc \
      --arg pr "$PR_URL" --arg num "$PR_NUM" --arg repo "$REPO" --arg diff "$DIFF" \
      '{text: ("lofi: regression on PR #" + $num + " (" + $repo + ") — " + $pr), diff: $diff}' \
      | curl -fsS -X POST -H 'Content-Type: application/json' --data-binary @- "$WEBHOOK_URL"
```

The `jq` step is used to safely encode the markdown diff (which may contain
quotes and newlines) into a JSON string. Customize the payload schema to match
the receiving service's expectations.

The notification step fires only when the diff step exited non-zero (i.e.
threshold was exceeded). It does not fire on every PR, only on regressions.

## Common adjustments

**Run only on labeled PRs:** add `if: contains(github.event.pull_request.labels.*.name, 'perf-check')` to the job.

**Pin the lofi-cli version** instead of `npm install -g`:
```yaml
      - run: npm install -g @closeup1202/lofi-cli@0.4.2
```
