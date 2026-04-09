import axios, { AxiosError } from 'axios'
import {
    LofiConnectionError,
    LofiNotFoundError,
    LofiUnexpectedError
} from './error'

export interface MethodDiff {
    signature: string
    baseMs: number
    headMs: number
    deltaMs: number
    regressed: boolean
}

export interface DiffResult {
    baseCommit: string
    headCommit: string
    diffs: MethodDiff[]
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

export class LofiClient {
    private readonly baseUrl: string

    constructor(baseUrl: string) {
        this.baseUrl = baseUrl.replace(/\/$/, '')
    }

    async diff(base: string, head: string): Promise<DiffResult> {
        return this.request(() =>
            axios.get(`${this.baseUrl}/actuator/lofi-diff`, {
                params: { base, head }
            })
        )
    }

    async snapshot(commitHash: string): Promise<DeploySnapshot> {
        return this.request(
            () => axios.get(`${this.baseUrl}/actuator/lofi/${commitHash}`),
            commitHash
        )
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
                throw new LofiUnexpectedError(
                    `HTTP ${axiosErr.response.status}`
                )
            }
            throw new LofiUnexpectedError((err as Error).message)
        }
    }
}