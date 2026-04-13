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
        console.error(chalk.red(`\nConnection failed — ${err.message}`))
        console.error(chalk.gray('  Check the actuator URL with the --url option'))
    } else if (err instanceof LofiNotFoundError) {
        console.error(chalk.red(`\nNo data found — ${err.message}`))
        console.error(chalk.gray('  Verify the commit hash is correct and that metrics were collected for that deploy'))
    } else if (err instanceof LofiUnexpectedError) {
        console.error(chalk.red(`\nError — ${err.message}`))
    } else {
        console.error(chalk.red('\nAn unknown error occurred'))
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
            console.error(chalk.red('\nInvalid format: lofi diff <base>..<head>'))
            console.error(chalk.gray('  Example: lofi diff a3f9c1..d82e04'))
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