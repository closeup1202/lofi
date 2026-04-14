export class LofiConnectionError extends Error {
    constructor(url: string) {
        super(`Cannot connect to ${url}`)
        this.name = 'LofiConnectionError'
    }
}

export class LofiNotFoundError extends Error {
    constructor(commitHash: string) {
        super(`No data found for commit: ${commitHash}`)
        this.name = 'LofiNotFoundError'
    }
}

export class LofiUnexpectedError extends Error {
    constructor(message: string) {
        super(`Unexpected error: ${message}`)
        this.name = 'LofiUnexpectedError'
    }
}
