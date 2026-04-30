import {describe, expect, it} from 'vitest'
import {compareVersions, shouldSkipUpdateCheck} from '../src/update-check'

describe('compareVersions', () => {
    it('returns 0 for identical versions', () => {
        expect(compareVersions('0.4.1', '0.4.1')).toBe(0)
        expect(compareVersions('1.2.3', '1.2.3')).toBe(0)
    })

    it('returns -1 when first is older (patch / minor / major)', () => {
        expect(compareVersions('0.4.0', '0.4.5')).toBe(-1)
        expect(compareVersions('0.4.99', '0.5.0')).toBe(-1)
        expect(compareVersions('0.99.0', '1.0.0')).toBe(-1)
    })

    it('returns 1 when first is newer', () => {
        expect(compareVersions('0.4.5', '0.4.0')).toBe(1)
        expect(compareVersions('1.0.0', '0.99.99')).toBe(1)
    })

    it('treats pre-release suffix as the release version (good enough for non-prerelease packages)', () => {
        expect(compareVersions('0.4.5-rc.1', '0.4.5')).toBe(0)
        expect(compareVersions('0.4.0', '0.4.5-rc.1')).toBe(-1)
    })

    it('handles missing patch component as 0', () => {
        expect(compareVersions('0.4', '0.4.0')).toBe(0)
        expect(compareVersions('0.4', '0.4.1')).toBe(-1)
    })
})

describe('shouldSkipUpdateCheck', () => {
    const tty = true
    const noTty = false

    it('skips when stdout is not a TTY (piped/redirected)', () => {
        expect(shouldSkipUpdateCheck({}, ['diff'], noTty)).toBe(true)
    })

    it('skips when CI env is set', () => {
        expect(shouldSkipUpdateCheck({CI: 'true'}, ['diff'], tty)).toBe(true)
    })

    it('skips when LOFI_NO_UPDATE_CHECK is truthy', () => {
        expect(shouldSkipUpdateCheck({LOFI_NO_UPDATE_CHECK: '1'}, ['diff'], tty)).toBe(true)
        expect(shouldSkipUpdateCheck({LOFI_NO_UPDATE_CHECK: 'yes'}, ['diff'], tty)).toBe(true)
    })

    it('does NOT skip when LOFI_NO_UPDATE_CHECK is falsy ("0", "false")', () => {
        expect(shouldSkipUpdateCheck({LOFI_NO_UPDATE_CHECK: '0'}, ['diff'], tty)).toBe(false)
        expect(shouldSkipUpdateCheck({LOFI_NO_UPDATE_CHECK: 'false'}, ['diff'], tty)).toBe(false)
        expect(shouldSkipUpdateCheck({LOFI_NO_UPDATE_CHECK: ''}, ['diff'], tty)).toBe(false)
    })

    it('skips when NO_UPDATE_NOTIFIER is set (cross-CLI convention)', () => {
        expect(shouldSkipUpdateCheck({NO_UPDATE_NOTIFIER: '1'}, ['diff'], tty)).toBe(true)
    })

    it('skips when --no-update-check flag is passed', () => {
        expect(shouldSkipUpdateCheck({}, ['diff', '--no-update-check'], tty)).toBe(true)
    })

    it('skips for --version / -V', () => {
        expect(shouldSkipUpdateCheck({}, ['--version'], tty)).toBe(true)
        expect(shouldSkipUpdateCheck({}, ['-V'], tty)).toBe(true)
    })

    it('skips for machine-readable --format values', () => {
        expect(shouldSkipUpdateCheck({}, ['diff', '--format', 'json'], tty)).toBe(true)
        expect(shouldSkipUpdateCheck({}, ['diff', '--format', 'markdown'], tty)).toBe(true)
        expect(shouldSkipUpdateCheck({}, ['diff', '--format', 'md'], tty)).toBe(true)
        expect(shouldSkipUpdateCheck({}, ['diff', '--format=json'], tty)).toBe(true)
        expect(shouldSkipUpdateCheck({}, ['diff', '--format=md'], tty)).toBe(true)
    })

    it('does NOT skip for --format table (the human-readable default)', () => {
        expect(shouldSkipUpdateCheck({}, ['diff', '--format', 'table'], tty)).toBe(false)
        expect(shouldSkipUpdateCheck({}, ['diff'], tty)).toBe(false)
    })

    it('does NOT skip for an interactive TTY run with no opt-out signals', () => {
        expect(shouldSkipUpdateCheck({}, ['diff', 'a..b', '--url', 'http://x'], tty)).toBe(false)
    })
})
