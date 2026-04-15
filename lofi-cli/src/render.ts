import chalk from 'chalk'
import {DeploySnapshot, DiffResult, MethodDiff, StatType, ThresholdOptions} from './client'

const DIFF_LINE = '─'.repeat(96)
const SNAPSHOT_LINE = '─'.repeat(61)

export type OutputFormat = 'table' | 'json' | 'markdown'

export interface RenderOptions extends ThresholdOptions {
    format?: OutputFormat
}

function shortSignature(signature: string): string {
    const parts = signature.split('.')
    const methodPart = parts[parts.length - 1]  // e.g. "create()"
    const className = parts[parts.length - 2]   // e.g. "OrderController"
    return `${className}.${methodPart}`
}

function getStatMs(d: MethodDiff, stat: StatType = 'avg'): {base: number; head: number; delta: number} {
    switch (stat) {
        case 'p95':
            return {base: d.baseP95Ms, head: d.headP95Ms, delta: d.headP95Ms - d.baseP95Ms}
        case 'p99':
            return {base: d.baseP99Ms, head: d.headP99Ms, delta: d.headP99Ms - d.baseP99Ms}
        default:
            return {base: d.baseMs, head: d.headMs, delta: d.deltaMs}
    }
}

function applyFilters(diffs: MethodDiff[], options: RenderOptions): MethodDiff[] {
    if (options.minCalls === undefined) return diffs
    return diffs.filter(d => {
        // Only filter when both commits have data — absent methods (count=0) pass through
        if (d.baseCount > 0 && d.headCount > 0) {
            return Math.min(d.baseCount, d.headCount) >= options.minCalls!
        }
        return true
    })
}

function exceedsThreshold(d: MethodDiff, options: RenderOptions): boolean {
    const {base, delta} = getStatMs(d, options.stat)
    if (delta <= 0) return false
    if (options.thresholdMs !== undefined) return delta > options.thresholdMs
    if (options.thresholdRate !== undefined && base > 0) return delta / base > options.thresholdRate
    return false
}

function thresholdLabel(options: ThresholdOptions): string | null {
    if (options.thresholdMs !== undefined) return `${options.thresholdMs}ms`
    if (options.thresholdRate !== undefined) return `${(options.thresholdRate * 100).toFixed(0)}%`
    return null
}

// ─── renderCheck ─────────────────────────────────────────────────────────────

function renderCheckTable(result: DiffResult, options: RenderOptions): void {
    const filtered = applyFilters(result.diffs, options)
    const exceeded = filtered.filter(d => exceedsThreshold(d, options))

    if (exceeded.length === 0) {
        console.log(chalk.green.bold('✓ All within threshold'))
        process.exit(0)
    }

    console.error(chalk.red.bold(`✗ ${exceeded.length} method(s) exceeded threshold`))
    for (const d of exceeded) {
        const {base, head, delta} = getStatMs(d, options.stat)
        const rate = base > 0 ? ` (+${((delta / base) * 100).toFixed(1)}%)` : ''
        console.error(chalk.red(`  ${shortSignature(d.signature)}  ${base.toFixed(2)}ms → ${head.toFixed(2)}ms  (+${delta.toFixed(2)}ms${rate})`))
    }
    process.exit(1)
}

function renderCheckJson(result: DiffResult, options: RenderOptions): void {
    const filtered = applyFilters(result.diffs, options)
    const exceeded = filtered.filter(d => exceedsThreshold(d, options))
    const stat = options.stat ?? 'avg'

    const output: Record<string, unknown> = {
        passed: exceeded.length === 0,
        stat,
        exceeded: exceeded.map(d => {
            const {base, head, delta} = getStatMs(d, stat)
            return {
                signature: d.signature,
                baseMs: base,
                headMs: head,
                deltaMs: delta,
                changeRate: base > 0 ? +(delta / base).toFixed(4) : null,
                baseCount: d.baseCount,
                headCount: d.headCount
            }
        })
    }

    if (options.thresholdMs !== undefined) output.threshold = {ms: options.thresholdMs}
    else if (options.thresholdRate !== undefined) output.threshold = {rate: options.thresholdRate}
    if (options.minCalls !== undefined) output.minCalls = options.minCalls

    console.log(JSON.stringify(output, null, 2))
    process.exit(exceeded.length > 0 ? 1 : 0)
}

function renderCheckMarkdown(result: DiffResult, options: RenderOptions): void {
    const filtered = applyFilters(result.diffs, options)
    const exceeded = filtered.filter(d => exceedsThreshold(d, options))
    const stat = options.stat ?? 'avg'
    const lines: string[] = []

    if (exceeded.length === 0) {
        lines.push('## Latency Check: ✓ All within threshold')
    } else {
        lines.push(`## Latency Check: ✗ ${exceeded.length} method(s) exceeded threshold`)
        lines.push('')
        lines.push(`| Method | Before (${stat}) | After (${stat}) | Delta | Change | Calls |`)
        lines.push('|--------|--------|-------|-------|--------|-------|')
        for (const d of exceeded) {
            const {base, head, delta} = getStatMs(d, stat)
            const sig = shortSignature(d.signature)
            const change = base > 0 ? `+${((delta / base) * 100).toFixed(1)}%` : '—'
            lines.push(`| ${sig} | ${base.toFixed(2)}ms | ${head.toFixed(2)}ms | +${delta.toFixed(2)}ms | ${change} | ${d.baseCount}→${d.headCount} |`)
        }
    }

    const label = thresholdLabel(options)
    if (label) {
        lines.push('')
        lines.push(`> Threshold: ${label}  ·  Stat: ${stat}`)
    }
    if (options.minCalls !== undefined) {
        lines.push(`> Min calls: ${options.minCalls}`)
    }

    console.log(lines.join('\n'))
    process.exit(exceeded.length > 0 ? 1 : 0)
}

export function renderCheck(result: DiffResult, options: RenderOptions): void {
    switch (options.format ?? 'table') {
        case 'json':     return renderCheckJson(result, options)
        case 'markdown': return renderCheckMarkdown(result, options)
        default:         return renderCheckTable(result, options)
    }
}

// ─── renderDiff ──────────────────────────────────────────────────────────────

function renderDiffTable(result: DiffResult, options: RenderOptions): boolean | null {
    const stat = options.stat ?? 'avg'
    const filtered = applyFilters(result.diffs, options)

    console.log()
    console.log(chalk.bold('Deploy Diff') + '  ' +
        chalk.gray(result.baseCommit) + ' → ' +
        chalk.white(result.headCommit)
    )

    const label = thresholdLabel(options)
    const statLabel = stat !== 'avg' ? stat.toUpperCase() : 'avg'
    console.log(chalk.gray(`Stat: ${statLabel}`) + (label ? chalk.gray(`  Threshold: ${label}`) : ''))
    if (options.minCalls !== undefined) console.log(chalk.gray(`Min calls: ${options.minCalls}`))

    console.log(chalk.gray(DIFF_LINE))
    console.log(
        chalk.gray('  Method'.padEnd(44)) +
        chalk.gray('Before'.padStart(10)) +
        chalk.gray('     ') +
        chalk.gray('After'.padStart(10)) +
        chalk.gray('  ') +
        chalk.gray('Delta'.padStart(10)) +
        chalk.gray('  ') +
        chalk.gray('Calls'.padStart(11))
    )
    console.log(chalk.gray(DIFF_LINE))

    const sortedDiffs = [...filtered].sort((a, b) => {
        const aS = getStatMs(a, stat)
        const bS = getStatMs(b, stat)
        return bS.delta - aS.delta
    })

    for (const d of sortedDiffs) {
        const {base, head, delta} = getStatMs(d, stat)
        const signature = shortSignature(d.signature).padEnd(41)
        const baseStr = `${base.toFixed(2)}ms`.padStart(10)
        const headStr = `${head.toFixed(2)}ms`.padStart(10)
        const deltaStr = `${delta > 0 ? '+' : ''}${delta.toFixed(2)}ms`.padStart(10)
        const callsStr = (d.baseCount > 0 || d.headCount > 0)
            ? `${d.baseCount}→${d.headCount}`.padStart(11)
            : ''.padStart(11)
        const arrow = delta > 0 ? ' ▲' : ' —'
        const exceeded = exceedsThreshold(d, options)

        if (exceeded) {
            console.log(chalk.red.bold(`  ${signature} ${baseStr}  →  ${headStr}  ${deltaStr}  ${callsStr}${arrow} 🔥`))
        } else if (d.regressed) {
            console.log(chalk.red(`  ${signature} ${baseStr}  →  ${headStr}  ${deltaStr}  ${callsStr}${arrow}`))
        } else {
            console.log(chalk.gray(`  ${signature} ${baseStr}  →  ${headStr}  ${deltaStr}  ${callsStr}${arrow}`))
        }
    }

    console.log(chalk.gray(DIFF_LINE))

    const regressions = filtered.filter(d => d.regressed)
    const exceededList = filtered.filter(d => exceedsThreshold(d, options))
    const hasThreshold = label !== null

    if (regressions.length > 0) {
        console.log(chalk.red.bold(`  ${regressions.length} regression(s) detected`))
    } else {
        console.log(chalk.green.bold('  No regressions detected'))
    }

    if (hasThreshold) {
        if (exceededList.length > 0) {
            console.log(chalk.red.bold(`  ${exceededList.length} exceeded threshold 🔥`))
        } else {
            console.log(chalk.green.bold('  All within threshold'))
        }
    }

    console.log()
    return hasThreshold ? exceededList.length > 0 : null
}

function renderDiffJson(result: DiffResult, options: RenderOptions): boolean | null {
    const stat = options.stat ?? 'avg'
    const filtered = applyFilters(result.diffs, options)
    const exceededList = filtered.filter(d => exceedsThreshold(d, options))
    const hasThreshold = thresholdLabel(options) !== null

    const output: Record<string, unknown> = {
        baseCommit: result.baseCommit,
        headCommit: result.headCommit,
        stat,
        regressions: filtered.filter(d => d.regressed).length,
        diffs: filtered.map(d => {
            const {base, head, delta} = getStatMs(d, stat)
            return {
                signature: d.signature,
                baseMs: base,
                headMs: head,
                deltaMs: delta,
                regressed: d.regressed,
                baseCount: d.baseCount,
                headCount: d.headCount
            }
        })
    }

    if (hasThreshold) {
        if (options.thresholdMs !== undefined) output.threshold = {ms: options.thresholdMs}
        else output.threshold = {rate: options.thresholdRate}
        output.exceeded = exceededList.map(d => {
            const {base, head, delta} = getStatMs(d, stat)
            return {
                signature: d.signature,
                baseMs: base,
                headMs: head,
                deltaMs: delta,
                changeRate: base > 0 ? +(delta / base).toFixed(4) : null,
                baseCount: d.baseCount,
                headCount: d.headCount
            }
        })
    }
    if (options.minCalls !== undefined) output.minCalls = options.minCalls

    console.log(JSON.stringify(output, null, 2))
    return hasThreshold ? exceededList.length > 0 : null
}

function renderDiffMarkdown(result: DiffResult, options: RenderOptions): boolean | null {
    const stat = options.stat ?? 'avg'
    const filtered = applyFilters(result.diffs, options)
    const sortedDiffs = [...filtered].sort((a, b) => {
        const aS = getStatMs(a, stat)
        const bS = getStatMs(b, stat)
        return bS.delta - aS.delta
    })
    const exceededList = filtered.filter(d => exceedsThreshold(d, options))
    const regressions = filtered.filter(d => d.regressed)
    const label = thresholdLabel(options)
    const hasThreshold = label !== null
    const lines: string[] = []

    lines.push(`## Deploy Diff: \`${result.baseCommit}\` → \`${result.headCommit}\``)
    lines.push('')

    const meta: string[] = [`Stat: ${stat}`]
    if (label) meta.push(`Threshold: ${label}`)
    if (options.minCalls !== undefined) meta.push(`Min calls: ${options.minCalls}`)
    lines.push(`> ${meta.join('  ·  ')}`)
    lines.push('')

    lines.push(`| Method | Before (${stat}) | After (${stat}) | Delta | Calls |`)
    lines.push('|--------|--------|-------|-------|-------|')

    for (const d of sortedDiffs) {
        const {base, head, delta} = getStatMs(d, stat)
        const sig = shortSignature(d.signature)
        const deltaStr = `${delta > 0 ? '+' : ''}${delta.toFixed(2)}ms`
        const flag = exceedsThreshold(d, options) ? ' 🔥' : d.regressed ? ' ▲' : ''
        const calls = `${d.baseCount}→${d.headCount}`
        lines.push(`| ${sig} | ${base.toFixed(2)}ms | ${head.toFixed(2)}ms | ${deltaStr}${flag} | ${calls} |`)
    }

    lines.push('')

    const summaryParts: string[] = []
    if (regressions.length > 0) {
        summaryParts.push(`**${regressions.length} regression(s) detected**`)
    } else {
        summaryParts.push('**No regressions detected**')
    }
    if (hasThreshold) {
        summaryParts.push(exceededList.length > 0
            ? `**${exceededList.length} exceeded threshold 🔥**`
            : '**All within threshold ✓**'
        )
    }
    lines.push(summaryParts.join(' · '))

    console.log(lines.join('\n'))
    return hasThreshold ? exceededList.length > 0 : null
}

export function renderDiff(result: DiffResult, options: RenderOptions = {}): boolean | null {
    switch (options.format ?? 'table') {
        case 'json':     return renderDiffJson(result, options)
        case 'markdown': return renderDiffMarkdown(result, options)
        default:         return renderDiffTable(result, options)
    }
}

// ─── renderSnapshot ───────────────────────────────────────────────────────────

export function renderSnapshot(snapshot: DeploySnapshot): void {
    console.log()
    console.log(chalk.bold('Snapshot') + '  ' + chalk.gray(snapshot.commitHash))
    console.log(chalk.gray(SNAPSHOT_LINE))
    console.log(chalk.gray(`  Deployed at: ${snapshot.deployedAt}`))
    console.log(chalk.gray(`  Metrics collected: ${snapshot.metrics.length}`))

    if (snapshot.metrics.length > 0) {
        const avgByMethod = new Map<string, { total: number; count: number }>()
        for (const m of snapshot.metrics) {
            const key = `${m.className}.${m.methodName}()`
            const curr = avgByMethod.get(key) ?? {total: 0, count: 0}
            avgByMethod.set(key, {total: curr.total + m.elapsedMs, count: curr.count + 1})
        }

        const sorted = [...avgByMethod.entries()]
            .sort((a, b) => (b[1].total / b[1].count) - (a[1].total / a[1].count))

        console.log()
        console.log(
            chalk.gray('  Method'.padEnd(45)) +
            chalk.gray('Avg'.padStart(9)) +
            chalk.gray('Calls'.padStart(7))
        )
        console.log(chalk.gray(SNAPSHOT_LINE))

        for (const [sig, {total, count}] of sorted) {
            const avgMs = total / count
            console.log(chalk.gray(
                `  ${shortSignature(sig).padEnd(44)} ${(avgMs.toFixed(2) + 'ms').padStart(8)} ${String(count).padStart(5)}`
            ))
        }
    }

    console.log()
}
