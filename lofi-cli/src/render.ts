import chalk from 'chalk'
import { DiffResult, DeploySnapshot } from './client'

const LINE = '─'.repeat(60)

export function renderDiff(result: DiffResult): void {
    console.log()
    console.log(chalk.bold('배포 비교') + '  ' +
        chalk.gray(result.baseCommit.slice(0, 7)) + ' → ' +
        chalk.white(result.headCommit.slice(0, 7))
    )
    console.log(chalk.gray(LINE))
    console.log(
        chalk.gray('  메서드'.padEnd(45)) +
        chalk.gray('이전'.padStart(8)) +
        chalk.gray('이후'.padStart(8)) +
        chalk.gray('변화'.padStart(10))
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
        console.log(chalk.red.bold(`  성능 저하 ${regressions.length}건 감지됨`))
    } else {
        console.log(chalk.green.bold('  성능 저하 없음'))
    }
    console.log()
}

export function renderSnapshot(snapshot: DeploySnapshot): void {
    console.log()
    console.log(chalk.bold('스냅샷') + '  ' + chalk.gray(snapshot.commitHash.slice(0, 7)))
    console.log(chalk.gray(LINE))
    console.log(chalk.gray(`  배포 시각: ${snapshot.deployedAt}`))
    console.log(chalk.gray(`  수집 메트릭: ${snapshot.metrics.length}건`))

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
            chalk.gray('  메서드'.padEnd(45)) +
            chalk.gray('평균'.padStart(8)) +
            chalk.gray('호출'.padStart(6))
        )
        console.log(chalk.gray(LINE))

        for (const [sig, { total, count }] of sorted) {
            const avg = total / count
            console.log(chalk.gray(
                `  ${sig.padEnd(44)} ${(avg.toFixed(0) + 'ms').padStart(7)} ${String(count).padStart(4)}회`
            ))
        }
    }

    console.log()
}
