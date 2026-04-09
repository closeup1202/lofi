export class LofiConnectionError extends Error {
    constructor(url: string) {
        super(`actuator에 연결할 수 없어요: ${url}`)
        this.name = 'LofiConnectionError'
    }
}

export class LofiNotFoundError extends Error {
    constructor(commitHash: string) {
        super(`커밋 데이터가 없어요: ${commitHash}`)
        this.name = 'LofiNotFoundError'
    }
}

export class LofiUnexpectedError extends Error {
    constructor(message: string) {
        super(`예상치 못한 오류가 발생했어요: ${message}`)
        this.name = 'LofiUnexpectedError'
    }
}