import chalk from 'chalk'
import { DiffResult, DeploySnapshot } from './client'

const LINE = '─'.repeat(60)

export function renderDiff(result: DiffResult): void {
    console.log()
    console.log(chalk.bold('Deploy Diff') + '  ' +
        chalk.gray(result.baseCommit.slice(0, 7)) + ' → ' +
        chalk.white(result.headCommit.slice(0, 7))
    )
    console.log(chalk.gray(LINE))
    console.log(
        chalk.gray('  Method'.padEnd(45)) +
        chalk.gray('Before'.padStart(8)) +
        chalk.gray('After'.padStart(8)) +
        chalk.gray('Delta'.padStart(10))
    )
    console.log(chalk.gray(LINE))

    for (const d of result.diffs) {
        const signature = d.signature.padEnd(44)
        const base = `${d.baseMs.toFixed(0)}ms`.padStart(7)
        const head = `${d.headMs.toFixed(0)}ms`.padStart(7)
        const delta = `${d.deltaMs > 0 ? '+' : ''}${d.deltaMs.toFixed(0)}ms`.padStart(8)
        const arrow = d.deltaMs > 0 ? ' ▲' : ' —'

        if (d.regressed) {
            console.log(chalk.red(`  ${signature} ${base}  →  ${head}  ${delta}${arrow}`))
        } else {
            console.log(chalk.gray(`  ${signature} ${base}  →  ${head}  ${delta}${arrow}`))
        }
    }

    console.log(chalk.gray(LINE))

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
    console.log(chalk.bold('Snapshot') + '  ' + chalk.gray(snapshot.commitHash.slice(0, 7)))
    console.log(chalk.gray(LINE))
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
            chalk.gray('Avg'.padStart(8)) +
            chalk.gray('Calls'.padStart(6))
        )
        console.log(chalk.gray(LINE))

        for (const [sig, { total, count }] of sorted) {
            const avg = total / count
            console.log(chalk.gray(
                `  ${sig.padEnd(44)} ${(avg.toFixed(0) + 'ms').padStart(7)} ${String(count).padStart(5)}`
            ))
        }
    }

    console.log()
}
