#!/usr/bin/env node
import { Command } from 'commander'
import chalk from 'chalk'
import { select } from '@inquirer/prompts'
import { LofiClient, CommitSummary } from './client'
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
    .version('0.2.0')

function handleError(err: unknown): never {
    if (err instanceof LofiConnectionError) {
        console.error(chalk.red(`\nConnection failed — ${err.message}`))
        console.error(chalk.gray('  Check the URL with the --url option'))
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

function formatCommitChoice(commit: CommitSummary): string {
    const date = new Date(commit.deployedAt).toLocaleString()
    return `${commit.commitHash}  (${date}, ${commit.metricCount} metrics)`
}

async function pickCommit(commits: CommitSummary[], message: string): Promise<string> {
    if (commits.length === 0) {
        console.error(chalk.red('\nNo recorded deploys found'))
        console.error(chalk.gray('  Make sure the app has received traffic after startup'))
        process.exit(1)
    }
    return select({
        message,
        choices: commits.map(c => ({ value: c.commitHash, name: formatCommitChoice(c) }))
    })
}

program
    .command('diff [range]')
    .description('Compare method latency between two deploys')
    .option('-U, --url <url>', 'server base url', 'http://localhost:8080')
    .action(async (range: string | undefined, options: { url: string }) => {
        try {
            const client = new LofiClient(options.url)

            let base: string
            let head: string

            if (range) {
                const parts = range.split('..')
                if (!parts[0] || !parts[1]) {
                    console.error(chalk.red('\nInvalid format: lofi diff <base>..<head>'))
                    console.error(chalk.gray('  Example: lofi diff a3f9c1..d82e04'))
                    process.exit(1)
                }
                base = parts[0]
                head = parts[1]
            } else {
                const commits = await client.commits()
                base = await pickCommit(commits, 'Select base commit (before):')
                head = await pickCommit(commits, 'Select head commit (after):')
            }

            const result = await client.diff(base, head)
            renderDiff(result)
        } catch (err) {
            handleError(err)
        }
    })

program
    .command('snapshot [commitHash]')
    .description('Show metrics for a specific deploy')
    .option('-U, --url <url>', 'server base url', 'http://localhost:8080')
    .action(async (commitHash: string | undefined, options: { url: string }) => {
        try {
            const client = new LofiClient(options.url)

            const hash = commitHash ?? await (async () => {
                const commits = await client.commits()
                return pickCommit(commits, 'Select a commit to inspect:')
            })()

            const snapshot = await client.snapshot(hash)
            renderSnapshot(snapshot)
        } catch (err) {
            handleError(err)
        }
    })

program.parseAsync(process.argv)
