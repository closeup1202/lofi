import axios from 'axios'
import * as fs from 'node:fs'
import * as path from 'node:path'
import * as os from 'node:os'

const REGISTRY_URL = 'https://registry.npmjs.org/@closeup1202/lofi-cli/latest'
const CACHE_TTL_MS = 24 * 60 * 60 * 1000
const FETCH_TIMEOUT_MS = 3000
const PACKAGE_NAME = '@closeup1202/lofi-cli'

interface CacheEntry {
    checkedAt: number
    latest: string | null
}

export function compareVersions(a: string, b: string): number {
    const parse = (v: string) => {
        const parts = v.split('.').slice(0, 3).map(p => parseInt(p, 10) || 0)
        while (parts.length < 3) parts.push(0)
        return parts
    }
    const [a1, a2, a3] = parse(a)
    const [b1, b2, b3] = parse(b)
    if (a1 !== b1) return a1 < b1 ? -1 : 1
    if (a2 !== b2) return a2 < b2 ? -1 : 1
    if (a3 !== b3) return a3 < b3 ? -1 : 1
    return 0
}

export function shouldSkipUpdateCheck(
    env: NodeJS.ProcessEnv,
    argv: string[],
    stdoutTTY: boolean
): boolean {
    const flag = env.LOFI_NO_UPDATE_CHECK
    if (flag && flag !== '0' && flag.toLowerCase() !== 'false') return true
    if (env.NO_UPDATE_NOTIFIER) return true
    if (env.CI) return true
    if (!stdoutTTY) return true
    if (argv.includes('--no-update-check')) return true
    if (argv.includes('--version') || argv.includes('-V')) return true
    for (let i = 0; i < argv.length; i++) {
        const a = argv[i]
        if (a === '--format' && argv[i + 1]) {
            const v = argv[i + 1]
            if (v === 'json' || v === 'markdown' || v === 'md') return true
        }
        if (a.startsWith('--format=')) {
            const v = a.slice('--format='.length)
            if (v === 'json' || v === 'markdown' || v === 'md') return true
        }
    }
    return false
}

function cacheFile(): string {
    const xdg = process.env.XDG_CACHE_HOME
    const base = xdg && xdg.trim() !== '' ? xdg : path.join(os.homedir(), '.cache')
    return path.join(base, 'lofi-cli', 'version-check.json')
}

function readCache(): CacheEntry | null {
    try {
        const raw = fs.readFileSync(cacheFile(), 'utf8')
        const parsed = JSON.parse(raw)
        if (
            typeof parsed?.checkedAt === 'number' &&
            (typeof parsed?.latest === 'string' || parsed?.latest === null)
        ) {
            return parsed as CacheEntry
        }
    } catch {
        // missing / corrupted — treat as no cache
    }
    return null
}

function writeCache(entry: CacheEntry): void {
    try {
        const file = cacheFile()
        fs.mkdirSync(path.dirname(file), {recursive: true})
        fs.writeFileSync(file, JSON.stringify(entry))
    } catch {
        // best-effort; never let cache failure break the CLI
    }
}

function isFresh(entry: CacheEntry): boolean {
    return Date.now() - entry.checkedAt < CACHE_TTL_MS
}

export function prepareUpdateBanner(currentVersion: string): string | null {
    const cached = readCache()
    if (!cached?.latest) return null
    if (compareVersions(currentVersion, cached.latest) >= 0) return null
    return `lofi: update available ${currentVersion} → ${cached.latest}  ·  npm install -g ${PACKAGE_NAME}  (set LOFI_NO_UPDATE_CHECK=1 to silence)`
}

export function kickoffAsyncRefresh(): void {
    const cached = readCache()
    if (cached && isFresh(cached)) return
    axios.get(REGISTRY_URL, {timeout: FETCH_TIMEOUT_MS})
        .then(res => {
            const latest = typeof res.data?.version === 'string' ? res.data.version : null
            writeCache({checkedAt: Date.now(), latest})
        })
        .catch(() => {
            // Stamp the attempt so we don't retry on every invocation when offline.
            writeCache({checkedAt: Date.now(), latest: cached?.latest ?? null})
        })
}
