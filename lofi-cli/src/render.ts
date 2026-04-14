import chalk from 'chalk'
import { DiffResult, DeploySnapshot } from './client'

const DIFF_LINE = '─'.repeat(84)
const SNAPSHOT_LINE = '─'.repeat(61)

function shortSignature(signature: string): string {
    const parts = signature.split('.')
    const methodPart = parts[parts.length - 1]  // e.g. "create()"
    const className = parts[parts.length - 2]   // e.g. "OrderController"
    return `${className}.${methodPart}`
}

export function renderDiff(result: DiffResult): void {
    console.log()
    console.log(chalk.bold('Deploy Diff') + '  ' +
        chalk.gray(result.baseCommit) + ' → ' +
        chalk.white(result.headCommit)
    )
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

    for (const d of result.diffs) {
        const signature = shortSignature(d.signature).padEnd(44)
        const base = `${d.baseMs.toFixed(2)}ms`.padStart(9)
        const head = `${d.headMs.toFixed(2)}ms`.padStart(9)
        const delta = `${d.deltaMs > 0 ? '+' : ''}${d.deltaMs.toFixed(2)}ms`.padStart(10)
        const arrow = d.deltaMs > 0 ? ' ▲' : ' —'

        if (d.regressed) {
            console.log(chalk.red(`  ${signature} ${base}  →  ${head}  ${delta}${arrow}`))
        } else {
            console.log(chalk.gray(`  ${signature} ${base}  →  ${head}  ${delta}${arrow}`))
        }
    }

    console.log(chalk.gray(DIFF_LINE))

    const regressions = result.diffs.filter(d => d.regressed)
    if (regressions.length > 0) {
        console.log(chalk.red.bold(`  ${regressions.length} regression(s) detected`))
    } else {
        console.log(chalk.green.bold('  No regressions detected'))
    }
    console.log()
}

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
            const curr = avgByMethod.get(key) ?? { total: 0, count: 0 }
            avgByMethod.set(key, { total: curr.total + m.elapsedMs, count: curr.count + 1 })
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

        for (const [sig, { total, count }] of sorted) {
            const avgMs = total / count
            console.log(chalk.gray(
                `  ${shortSignature(sig).padEnd(44)} ${(avgMs.toFixed(2) + 'ms').padStart(8)} ${String(count).padStart(5)}`
            ))
        }
    }

    console.log()
}
