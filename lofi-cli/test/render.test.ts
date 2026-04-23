import {describe, expect, it} from 'vitest'
import {
    applyFilters,
    exceedsThreshold,
    getStatMs,
    isRegressed,
    shortSignature,
    thresholdLabel
} from '../src/render'
import {MethodDiff} from '../src/client'

function diff(overrides: Partial<MethodDiff> = {}): MethodDiff {
    return {
        signature: 'com.example.OrderController.create()',
        baseMs: 10,
        headMs: 12,
        deltaMs: 2,
        regressed: false,
        baseP95Ms: 15,
        headP95Ms: 18,
        baseP99Ms: 20,
        headP99Ms: 25,
        baseCount: 100,
        headCount: 100,
        ...overrides
    }
}

describe('shortSignature', () => {
    it('keeps only the trailing class.method()', () => {
        expect(shortSignature('com.example.api.OrderController.create()')).toBe('OrderController.create()')
    })
})

describe('getStatMs', () => {
    it('defaults to avg', () => {
        const r = getStatMs(diff())
        expect(r.base).toBe(10)
        expect(r.head).toBe(12)
        expect(r.delta).toBe(2)
    })

    it('reads p95 fields when stat=p95', () => {
        const r = getStatMs(diff(), 'p95')
        expect(r.base).toBe(15)
        expect(r.head).toBe(18)
        expect(r.delta).toBe(3)
    })

    it('reads p99 fields when stat=p99', () => {
        const r = getStatMs(diff(), 'p99')
        expect(r.delta).toBe(5)
    })
})

describe('applyFilters', () => {
    it('passes through when minCalls is unset', () => {
        const diffs = [diff({baseCount: 1, headCount: 1})]
        expect(applyFilters(diffs, {})).toHaveLength(1)
    })

    it('drops methods below minCalls when both commits have data', () => {
        const diffs = [diff({baseCount: 5, headCount: 100})]
        expect(applyFilters(diffs, {minCalls: 30})).toHaveLength(0)
    })

    it('keeps method-absent rows (count=0 in either side)', () => {
        const newMethod = diff({baseCount: 0, headCount: 50})
        expect(applyFilters([newMethod], {minCalls: 100})).toHaveLength(1)
    })
})

describe('isRegressed', () => {
    it('returns false when delta <= 0', () => {
        expect(isRegressed(diff({deltaMs: 0}), 'avg', 0.1)).toBe(false)
    })

    it('returns true when delta exceeds threshold rate', () => {
        expect(isRegressed(diff({baseMs: 10, headMs: 13, deltaMs: 3}), 'avg', 0.2)).toBe(true)
    })

    it('handles base=0 (new method) as regression when delta > 0', () => {
        expect(isRegressed(diff({baseMs: 0, headMs: 5, deltaMs: 5}), 'avg', 0.5)).toBe(true)
    })
})

describe('exceedsThreshold', () => {
    it('returns false when no threshold is set', () => {
        expect(exceedsThreshold(diff(), {})).toBe(false)
    })

    it('thresholdMs: triggers above absolute delta', () => {
        expect(exceedsThreshold(diff({deltaMs: 51}), {thresholdMs: 50})).toBe(true)
        expect(exceedsThreshold(diff({deltaMs: 49}), {thresholdMs: 50})).toBe(false)
    })

    it('thresholdRate: triggers above relative delta', () => {
        const d = diff({baseMs: 10, headMs: 13, deltaMs: 3})
        expect(exceedsThreshold(d, {thresholdRate: 0.2})).toBe(true)
        expect(exceedsThreshold(d, {thresholdRate: 0.5})).toBe(false)
    })

    it('thresholdRate with base=0 never triggers (avoids divide-by-zero)', () => {
        const d = diff({baseMs: 0, headMs: 100, deltaMs: 100})
        expect(exceedsThreshold(d, {thresholdRate: 0.2})).toBe(false)
    })

    it('respects stat selection', () => {
        const d = diff({baseMs: 10, headMs: 11, deltaMs: 1, baseP95Ms: 10, headP95Ms: 30})
        expect(exceedsThreshold(d, {thresholdMs: 5, stat: 'avg'})).toBe(false)
        expect(exceedsThreshold(d, {thresholdMs: 5, stat: 'p95'})).toBe(true)
    })
})

describe('thresholdLabel', () => {
    it('formats ms', () => {
        expect(thresholdLabel({thresholdMs: 50})).toBe('50ms')
    })

    it('formats rate as percentage', () => {
        expect(thresholdLabel({thresholdRate: 0.2})).toBe('20%')
    })

    it('returns null when neither is set', () => {
        expect(thresholdLabel({})).toBeNull()
    })
})
