#!/usr/bin/env node
import {Command} from 'commander'
import chalk from 'chalk'
import {select} from '@inquirer/prompts'
import {CommitSummary, LofiClient, StatType} from './client'
import {OutputFormat, renderCheck, renderDiff, renderSnapshot} from './render'
import {LofiConnectionError, LofiNotFoundError, LofiUnexpectedError} from './error'

const program = new Command()

program
    .name('lofi')
    .description('Method-level latency regressions between deploys')
    .version('0.3.0')
    .addHelpText('after', `
Common Options:
  --url <url>                Target URL (default: http://localhost:8080)
  --stat <avg|p95|p99>       Latency stat to compare: avg, p95, p99 (default: avg)
  --threshold-ms <ms>        Absolute latency threshold in ms (diff, check)
  --threshold-rate <rate>    Relative threshold — 0.2 = 20% (diff, check)
  --min-calls <n>            Skip methods with fewer than n calls in either deploy (diff, check)
  --format <table|json|md>   Output format (default: table)

  --threshold-ms and --threshold-rate are mutually exclusive.

Examples:
  lofi diff a3f9c1..d82e04 --url http://localhost:8080
  lofi diff a3f9c1..d82e04 --stat p95 --min-calls 30
  lofi check a3f9c1..d82e04 --threshold-ms 50 --url https://staging.myapp.com
  lofi snapshot a3f9c1 --url http://localhost:8080

Run 'lofi <command> --help' for full option details.`)

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
    const date = new Date(commit.deployedAt).toLocaleString(undefined, {timeZoneName: 'short'})
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
        choices: commits.map(c => ({value: c.commitHash, name: formatCommitChoice(c)}))
    })
}

const VALID_FORMATS: OutputFormat[] = ['table', 'json', 'markdown']
const VALID_STATS: StatType[] = ['avg', 'p95', 'p99']

function parseFormat(value: string): OutputFormat {
    if (!VALID_FORMATS.includes(value as OutputFormat)) {
        console.error(chalk.red(`Invalid format: "${value}". Use table, json, or markdown`))
        process.exit(1)
    }
    return value as OutputFormat
}

function parseStat(value: string): StatType {
    if (!VALID_STATS.includes(value as StatType)) {
        console.error(chalk.red(`Invalid stat: "${value}". Use avg, p95, or p99`))
        process.exit(1)
    }
    return value as StatType
}

program
    .command('diff [range]')
    .description('Compare method latency between two deploys')
    .option('--url <url>', 'server base url', 'http://localhost:8080')
    .option('--threshold-ms <ms>', 'absolute latency threshold (ms)', parseFloat)
    .option('--threshold-rate <rate>', 'relative threshold (0.2 = 20%)', parseFloat)
    .option('--stat <stat>', 'latency stat to compare: avg, p95, p99', parseStat, 'avg' as StatType)
    .option('--min-calls <n>', 'skip methods with fewer than n calls in either deploy', parseInt)
    .option('--format <format>', 'output format: table, json, markdown', parseFormat, 'table' as OutputFormat)
    .action(async (range: string | undefined, options: {
        url: string
        thresholdMs?: number
        thresholdRate?: number
        stat: StatType
        minCalls?: number
        format: OutputFormat
    }) => {
        try {
            if (options.thresholdMs !== undefined && options.thresholdRate !== undefined) {
                console.error(chalk.red('Use either --threshold-ms or --threshold-rate'))
                process.exit(1)
            }

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
            const exceeded = renderDiff(result, {
                thresholdMs: options.thresholdMs,
                thresholdRate: options.thresholdRate,
                stat: options.stat,
                minCalls: options.minCalls,
                format: options.format
            })

            if (exceeded === true) process.exit(1)
            if (exceeded === false) process.exit(0)

        } catch (err) {
            handleError(err)
        }
    })

program
    .command('snapshot [commitHash]')
    .description('Show metrics for a specific deploy')
    .option('--url <url>', 'server base url', 'http://localhost:8080')
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

program
    .command('check [range]')
    .description('Fail if latency regression exceeds threshold (CI mode)')
    .option('--url <url>', 'server base url', 'http://localhost:8080')
    .option('--threshold-ms <ms>', 'absolute latency threshold (ms)', parseFloat)
    .option('--threshold-rate <rate>', 'relative threshold (0.2 = 20%)', parseFloat)
    .option('--stat <stat>', 'latency stat to compare: avg, p95, p99', parseStat, 'avg' as StatType)
    .option('--min-calls <n>', 'skip methods with fewer than n calls in either deploy', parseInt)
    .option('--format <format>', 'output format: table, json, markdown', parseFormat, 'table' as OutputFormat)
    .action(async (range: string | undefined, options: {
        url: string
        thresholdMs?: number
        thresholdRate?: number
        stat: StatType
        minCalls?: number
        format: OutputFormat
    }) => {
        try {
            if (options.thresholdMs !== undefined && options.thresholdRate !== undefined) {
                console.error(chalk.red('Use either --threshold-ms or --threshold-rate'))
                process.exit(1)
            }

            if (options.thresholdMs === undefined && options.thresholdRate === undefined) {
                console.warn(chalk.yellow('\n⚠ No threshold set — lofi check has no effect without one.'))
                console.warn(chalk.gray('  Specify a threshold to define a CI gate:'))
                console.warn(chalk.gray('    --threshold-ms <ms>      absolute delta  (e.g. --threshold-ms 50)'))
                console.warn(chalk.gray('    --threshold-rate <rate>  relative delta  (e.g. --threshold-rate 0.2)'))
                console.warn(chalk.gray('\n  Example:'))
                console.warn(chalk.gray('    lofi check a3f9c1..d82e04 --threshold-ms 50'))
                console.warn(chalk.gray('    lofi check a3f9c1..d82e04 --threshold-rate 0.2\n'))
                process.exit(0)
            }

            const client = new LofiClient(options.url)

            let base: string
            let head: string

            if (range) {
                const parts = range.split('..')
                if (!parts[0] || !parts[1]) {
                    console.error(chalk.red('\nInvalid format: lofi check <base>..<head>'))
                    console.error(chalk.gray('  Example: lofi check a3f9c1..d82e04'))
                    process.exit(1)
                }
                base = parts[0]
                head = parts[1]
            } else {
                const commits = await client.commits()
                base = await pickCommit(commits, 'Select base commit (before):')
                head = await pickCommit(commits, 'Select head commit (after):')
            }

            let result
            try {
                result = await client.diff(base, head)
            } catch (err) {
                if (err instanceof LofiNotFoundError) {
                    console.warn(chalk.yellow(`⚠ ${err.message} — skipping check`))
                    process.exit(0)
                }
                throw err
            }

            renderCheck(result, {
                thresholdMs: options.thresholdMs,
                thresholdRate: options.thresholdRate,
                stat: options.stat,
                minCalls: options.minCalls,
                format: options.format
            })

        } catch (err) {
            handleError(err)
        }
    })

program.parseAsync(process.argv).catch((err) => {
    console.error(err)
    process.exit(1)
})
