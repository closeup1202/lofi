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
    console.log(chalk.gray(`  수집된 메트릭: ${snapshot.metrics.length}건`))
    console.log(chalk.gray(`  수집 시각: ${snapshot.deployedAt}`))
    console.log()
}