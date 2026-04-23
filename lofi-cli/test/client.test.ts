import {beforeEach, describe, expect, it, vi} from 'vitest'
import axios from 'axios'
import {LofiClient} from '../src/client'
import {LofiConnectionError, LofiNotFoundError} from '../src/error'

vi.mock('axios')

const mockedAxios = axios as unknown as {
    get: ReturnType<typeof vi.fn>
    isAxiosError: typeof axios.isAxiosError
}

describe('LofiClient.resolveMode + URL switching', () => {
    beforeEach(() => {
        vi.resetAllMocks()
        // Restore the real isAxiosError function — vi.mock replaces it with undefined
        mockedAxios.isAxiosError = ((err: unknown): err is import('axios').AxiosError =>
            !!(err && typeof err === 'object' && 'isAxiosError' in err)) as typeof axios.isAxiosError
    })

    it('resolves to backend mode when GET /lofi succeeds', async () => {
        mockedAxios.get = vi.fn().mockResolvedValue({data: []})
        const client = new LofiClient('http://example.com')

        await client.commits()

        expect(mockedAxios.get).toHaveBeenCalledWith('http://example.com/lofi', {timeout: 3000})
        expect(mockedAxios.get).toHaveBeenLastCalledWith('http://example.com/lofi')
    })

    it('falls back to actuator mode when /lofi returns an HTTP error', async () => {
        const httpErr = {isAxiosError: true, response: {status: 404}}
        mockedAxios.get = vi.fn()
            .mockRejectedValueOnce(httpErr)             // resolveMode probe
            .mockResolvedValueOnce({data: []})          // actual /actuator/lofi call

        const client = new LofiClient('http://example.com')
        await client.commits()

        expect(mockedAxios.get).toHaveBeenLastCalledWith('http://example.com/actuator/lofi')
    })

    it('throws LofiConnectionError when probe fails with no response', async () => {
        const netErr = {isAxiosError: true, response: undefined}
        mockedAxios.get = vi.fn().mockRejectedValueOnce(netErr)

        const client = new LofiClient('http://offline.example.com')

        await expect(client.commits()).rejects.toBeInstanceOf(LofiConnectionError)
    })

    it('strips trailing slash from baseUrl', async () => {
        mockedAxios.get = vi.fn().mockResolvedValue({data: []})
        const client = new LofiClient('http://example.com/')

        await client.commits()

        expect(mockedAxios.get).toHaveBeenCalledWith('http://example.com/lofi', {timeout: 3000})
    })

    it('snapshot 404 maps to LofiNotFoundError with the requested commit hash', async () => {
        mockedAxios.get = vi.fn()
            .mockResolvedValueOnce({data: []})                                              // probe → backend
            .mockRejectedValueOnce({isAxiosError: true, response: {status: 404}})           // snapshot 404

        const client = new LofiClient('http://example.com')

        await expect(client.snapshot('a3f9c1d')).rejects.toBeInstanceOf(LofiNotFoundError)
    })

    it('caches mode across calls — only one probe', async () => {
        mockedAxios.get = vi.fn().mockResolvedValue({data: []})
        const client = new LofiClient('http://example.com')

        await client.commits()
        await client.diff('a', 'b')

        const probeCalls = mockedAxios.get.mock.calls.filter(c => c[1]?.timeout === 3000)
        expect(probeCalls).toHaveLength(1)
    })
})
