import {describe, expect, it} from 'vitest'
import {
    applyFilters,
    exceedsThreshold,
    formatMs,
    getStatMs,
    isRegressed,
    normalizeFormat,
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

describe('formatMs', () => {
    it('uses ms below 1 second', () => {
        expect(formatMs(0)).toBe('0.00ms')
        expect(formatMs(176.65)).toBe('176.65ms')
        expect(formatMs(999.99)).toBe('999.99ms')
    })

    it('switches to seconds at 1000ms+', () => {
        expect(formatMs(1000)).toBe('1.00s')
        expect(formatMs(45057.72)).toBe('45.06s')
        expect(formatMs(59999)).toBe('60.00s')
    })

    it('switches to minutes+seconds at 60000ms+', () => {
        expect(formatMs(60_000)).toBe('1m')
        expect(formatMs(125_000)).toBe('2m5s')
        expect(formatMs(3_600_000)).toBe('60m')
    })

    it('preserves negative sign without withSign flag', () => {
        expect(formatMs(-6.08)).toBe('-6.08ms')
        expect(formatMs(-45057.72)).toBe('-45.06s')
    })

    it('adds + sign for positive values when withSign=true', () => {
        expect(formatMs(45057.72, true)).toBe('+45.06s')
        expect(formatMs(176.65, true)).toBe('+176.65ms')
        expect(formatMs(0, true)).toBe('0.00ms')
        expect(formatMs(-6.08, true)).toBe('-6.08ms')
    })
})

describe('normalizeFormat', () => {
    it('passes canonical names through', () => {
        expect(normalizeFormat('table')).toBe('table')
        expect(normalizeFormat('json')).toBe('json')
        expect(normalizeFormat('markdown')).toBe('markdown')
    })

    it('accepts "md" as alias for "markdown"', () => {
        expect(normalizeFormat('md')).toBe('markdown')
    })

    it('returns null for unknown values', () => {
        expect(normalizeFormat('yaml')).toBeNull()
        expect(normalizeFormat('')).toBeNull()
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
