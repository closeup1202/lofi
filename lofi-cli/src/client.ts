import axios, { AxiosError } from 'axios'
import {
    LofiConnectionError,
    LofiNotFoundError,
    LofiUnexpectedError
} from './error'

export interface CommitSummary {
    commitHash: string
    deployedAt: string
    metricCount: number
}

export interface MethodDiff {
    signature: string
    baseMs: number
    headMs: number
    deltaMs: number
    regressed: boolean
    baseP95Ms: number
    headP95Ms: number
    baseP99Ms: number
    headP99Ms: number
    baseCount: number
    headCount: number
}

export interface DiffResult {
    baseCommit: string
    headCommit: string
    diffs: MethodDiff[]
    regressionThreshold: number
}

export interface MethodMetric {
    className: string
    methodName: string
    elapsedMs: number
    recordedAt: string
}

export interface DeploySnapshot {
    commitHash: string
    deployedAt: string
    metrics: MethodMetric[]
}

export type StatType = 'avg' | 'p95' | 'p99'

export interface ThresholdOptions {
    thresholdMs?: number
    thresholdRate?: number
    stat?: StatType
    minCalls?: number
}

type Mode = 'backend' | 'actuator'

export class LofiClient {
    private readonly baseUrl: string
    private mode: Mode | null = null

    constructor(baseUrl: string) {
        this.baseUrl = baseUrl.replace(/\/$/, '')
    }

    private async resolveMode(): Promise<Mode> {
        if (this.mode) return this.mode
        try {
            await axios.get(`${this.baseUrl}/lofi`, { timeout: 3000 })
            this.mode = 'backend'
        } catch (err) {
            if (axios.isAxiosError(err) && !err.response) {
                throw new LofiConnectionError(this.baseUrl)
            }
            this.mode = 'actuator'
        }
        return this.mode
    }

    async commits(): Promise<CommitSummary[]> {
        const mode = await this.resolveMode()
        const url = mode === 'backend'
            ? `${this.baseUrl}/lofi`
            : `${this.baseUrl}/actuator/lofi`
        return this.request(() => axios.get(url))
    }

    async diff(base: string, head: string): Promise<DiffResult> {
        const mode = await this.resolveMode()
        const url = mode === 'backend'
            ? `${this.baseUrl}/lofi/diff`
            : `${this.baseUrl}/actuator/lofi/diff`
        return this.request(() => axios.get(url, { params: { base, head } }))
    }

    async snapshot(commitHash: string): Promise<DeploySnapshot> {
        const mode = await this.resolveMode()
        const url = mode === 'backend'
            ? `${this.baseUrl}/lofi/${commitHash}`
            : `${this.baseUrl}/actuator/lofi/${commitHash}`
        return this.request(() => axios.get(url), commitHash)
    }

    private async request<T>(
        fn: () => Promise<{ data: T }>,
        commitHash?: string
    ): Promise<T> {
        try {
            const { data } = await fn()
            return data
        } catch (err) {
            if (axios.isAxiosError(err)) {
                const axiosErr = err as AxiosError
                if (!axiosErr.response) {
                    throw new LofiConnectionError(this.baseUrl)
                }
                if (axiosErr.response.status === 404 && commitHash) {
                    throw new LofiNotFoundError(commitHash)
                }
                throw new LofiUnexpectedError(`HTTP ${axiosErr.response.status}`)
            }
            throw new LofiUnexpectedError((err as Error).message)
        }
    }
}
