import chalk from 'chalk'
import {DeploySnapshot, DiffResult, MethodDiff, ThresholdOptions} from './client'

const DIFF_LINE = '─'.repeat(84)
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

function exceedsThreshold(d: MethodDiff, options: ThresholdOptions): boolean {
    if (d.deltaMs <= 0) return false
    if (options.thresholdMs !== undefined) return d.deltaMs > options.thresholdMs
    if (options.thresholdRate !== undefined && d.baseMs > 0) return d.deltaMs / d.baseMs > options.thresholdRate
    return false
}

function thresholdLabel(options: ThresholdOptions): string | null {
    if (options.thresholdMs !== undefined) return `${options.thresholdMs}ms`
    if (options.thresholdRate !== undefined) return `${(options.thresholdRate * 100).toFixed(0)}%`
    return null
}

// ─── renderCheck ─────────────────────────────────────────────────────────────

function renderCheckTable(result: DiffResult, options: RenderOptions): void {
    const exceeded = result.diffs.filter(d => exceedsThreshold(d, options))

    if (exceeded.length === 0) {
        console.log(chalk.green.bold('✓ All within threshold'))
        process.exit(0)
    }

    console.error(chalk.red.bold(`✗ ${exceeded.length} method(s) exceeded threshold`))
    for (const d of exceeded) {
        const rate = d.baseMs > 0 ? ` (+${((d.deltaMs / d.baseMs) * 100).toFixed(1)}%)` : ''
        console.error(chalk.red(`  ${shortSignature(d.signature)}  ${d.baseMs.toFixed(2)}ms → ${d.headMs.toFixed(2)}ms  (+${d.deltaMs.toFixed(2)}ms${rate})`))
    }
    process.exit(1)
}

function renderCheckJson(result: DiffResult, options: RenderOptions): void {
    const exceeded = result.diffs.filter(d => exceedsThreshold(d, options))

    const output: Record<string, unknown> = {
        passed: exceeded.length === 0,
        exceeded: exceeded.map(d => ({
            signature: d.signature,
            baseMs: d.baseMs,
            headMs: d.headMs,
            deltaMs: d.deltaMs,
            changeRate: d.baseMs > 0 ? +(d.deltaMs / d.baseMs).toFixed(4) : null
        }))
    }

    const label = thresholdLabel(options)
    if (options.thresholdMs !== undefined) output.threshold = {ms: options.thresholdMs}
    else if (options.thresholdRate !== undefined) output.threshold = {rate: options.thresholdRate}

    console.log(JSON.stringify(output, null, 2))
    process.exit(exceeded.length > 0 ? 1 : 0)
}

function renderCheckMarkdown(result: DiffResult, options: RenderOptions): void {
    const exceeded = result.diffs.filter(d => exceedsThreshold(d, options))
    const lines: string[] = []

    if (exceeded.length === 0) {
        lines.push('## Latency Check: ✓ All within threshold')
    } else {
        lines.push(`## Latency Check: ✗ ${exceeded.length} method(s) exceeded threshold`)
        lines.push('')
        lines.push('| Method | Before | After | Delta | Change |')
        lines.push('|--------|--------|-------|-------|--------|')
        for (const d of exceeded) {
            const sig = shortSignature(d.signature)
            const change = d.baseMs > 0 ? `+${((d.deltaMs / d.baseMs) * 100).toFixed(1)}%` : '—'
            lines.push(`| ${sig} | ${d.baseMs.toFixed(2)}ms | ${d.headMs.toFixed(2)}ms | +${d.deltaMs.toFixed(2)}ms | ${change} |`)
        }
    }

    const label = thresholdLabel(options)
    if (label) {
        lines.push('')
        lines.push(`> Threshold: ${label}`)
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
    console.log()
    console.log(chalk.bold('Deploy Diff') + '  ' +
        chalk.gray(result.baseCommit) + ' → ' +
        chalk.white(result.headCommit)
    )

    const label = thresholdLabel(options)
    if (label) console.log(chalk.gray(`Threshold: ${label}`))

    console.log(chalk.gray(DIFF_LINE))
    console.log(
        chalk.gray('  Method'.padEnd(47)) +
        chalk.gray('Before'.padStart(9)) +
        chalk.gray('     ') +
        chalk.gray('After'.padStart(9)) +
        chalk.gray('  ') +
        chalk.gray('Delta'.padStart(10))
    )
    console.log(chalk.gray(DIFF_LINE))

    const sortedDiffs = [...result.diffs].sort((a, b) => b.deltaMs - a.deltaMs)

    for (const d of sortedDiffs) {
        const signature = shortSignature(d.signature).padEnd(44)
        const base = `${d.baseMs.toFixed(2)}ms`.padStart(9)
        const head = `${d.headMs.toFixed(2)}ms`.padStart(9)
        const delta = `${d.deltaMs > 0 ? '+' : ''}${d.deltaMs.toFixed(2)}ms`.padStart(10)
        const arrow = d.deltaMs > 0 ? ' ▲' : ' —'
        const exceeded = exceedsThreshold(d, options)

        if (exceeded) {
            console.log(chalk.red.bold(`  ${signature} ${base}  →  ${head}  ${delta}${arrow} 🔥`))
        } else if (d.regressed) {
            console.log(chalk.red(`  ${signature} ${base}  →  ${head}  ${delta}${arrow}`))
        } else {
            console.log(chalk.gray(`  ${signature} ${base}  →  ${head}  ${delta}${arrow}`))
        }
    }

    console.log(chalk.gray(DIFF_LINE))

    const regressions = result.diffs.filter(d => d.regressed)
    const exceededList = result.diffs.filter(d => exceedsThreshold(d, options))
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
    const exceededList = result.diffs.filter(d => exceedsThreshold(d, options))
    const hasThreshold = thresholdLabel(options) !== null

    const output: Record<string, unknown> = {
        baseCommit: result.baseCommit,
        headCommit: result.headCommit,
        regressions: result.diffs.filter(d => d.regressed).length,
        diffs: result.diffs.map(d => ({
            signature: d.signature,
            baseMs: d.baseMs,
            headMs: d.headMs,
            deltaMs: d.deltaMs,
            regressed: d.regressed
        }))
    }

    if (hasThreshold) {
        if (options.thresholdMs !== undefined) output.threshold = {ms: options.thresholdMs}
        else output.threshold = {rate: options.thresholdRate}
        output.exceeded = exceededList.map(d => ({
            signature: d.signature,
            baseMs: d.baseMs,
            headMs: d.headMs,
            deltaMs: d.deltaMs,
            changeRate: d.baseMs > 0 ? +(d.deltaMs / d.baseMs).toFixed(4) : null
        }))
    }

    console.log(JSON.stringify(output, null, 2))
    return hasThreshold ? exceededList.length > 0 : null
}

function renderDiffMarkdown(result: DiffResult, options: RenderOptions): boolean | null {
    const sortedDiffs = [...result.diffs].sort((a, b) => b.deltaMs - a.deltaMs)
    const exceededList = result.diffs.filter(d => exceedsThreshold(d, options))
    const regressions = result.diffs.filter(d => d.regressed)
    const label = thresholdLabel(options)
    const hasThreshold = label !== null
    const lines: string[] = []

    lines.push(`## Deploy Diff: \`${result.baseCommit}\` → \`${result.headCommit}\``)
    lines.push('')

    if (label) {
        lines.push(`> Threshold: ${label}`)
        lines.push('')
    }

    lines.push('| Method | Before | After | Delta |')
    lines.push('|--------|--------|-------|-------|')

    for (const d of sortedDiffs) {
        const sig = shortSignature(d.signature)
        const delta = `${d.deltaMs > 0 ? '+' : ''}${d.deltaMs.toFixed(2)}ms`
        const flag = exceedsThreshold(d, options) ? ' 🔥' : d.regressed ? ' ▲' : ''
        lines.push(`| ${sig} | ${d.baseMs.toFixed(2)}ms | ${d.headMs.toFixed(2)}ms | ${delta}${flag} |`)
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
