#!/usr/bin/env node
import { Command } from 'commander'
import chalk from 'chalk'
import { LofiClient } from './client'
import { renderDiff, renderSnapshot } from './render'
import {
    LofiConnectionError,
    LofiNotFoundError,
    LofiUnexpectedError
} from './error'

const program = new Command()

program
    .name('lofi')
    .description('Method-level deploy diff for Spring Boot teams')
    .version('0.1.0')

function handleError(err: unknown): never {
    if (err instanceof LofiConnectionError) {
        console.error(chalk.red(`\n연결 실패 — ${err.message}`))
        console.error(chalk.gray('  --url 옵션으로 actuator 주소를 확인해주세요'))
    } else if (err instanceof LofiNotFoundError) {
        console.error(chalk.red(`\n데이터 없음 — ${err.message}`))
        console.error(chalk.gray('  커밋 해시가 올바른지, 해당 배포 데이터가 수집됐는지 확인해주세요'))
    } else if (err instanceof LofiUnexpectedError) {
        console.error(chalk.red(`\n오류 — ${err.message}`))
    } else {
        console.error(chalk.red('\n알 수 없는 오류가 발생했어요'))
        console.error(err)
    }
    process.exit(1)
}

program
    .command('diff <range>')
    .description('Compare method latency between two deploys')
    .option('-u, --url <url>', 'actuator base url', 'http://localhost:8080')
    .action(async (range: string, options: { url: string }) => {
        const [base, head] = range.split('..')
        if (!base || !head) {
            console.error(chalk.red('\n올바른 형식: lofi diff <base>..<head>'))
            console.error(chalk.gray('  예시: lofi diff a3f9c1..d82e04'))
            process.exit(1)
        }
        try {
            const client = new LofiClient(options.url)
            const result = await client.diff(base, head)
            renderDiff(result)
        } catch (err) {
            handleError(err)
        }
    })

program
    .command('snapshot <commitHash>')
    .description('Show metrics for a specific deploy')
    .option('-u, --url <url>', 'actuator base url', 'http://localhost:8080')
    .action(async (commitHash: string, options: { url: string }) => {
        try {
            const client = new LofiClient(options.url)
            const snapshot = await client.snapshot(commitHash)
            renderSnapshot(snapshot)
        } catch (err) {
            handleError(err)
        }
    })

program.parseAsync(process.argv)