import { describe, expect, it } from 'vitest'
import { formatMinor, fractionDigits, splitMinor } from '../src/lib/money'

/**
 * The money rule, held at the last step before a number reaches a human.
 *
 * Tally's own component took a major-unit float. These tests are what stop that creeping back in
 * through a "small" refactor.
 */
describe('money', () => {
  it('formats whole minor units without ever holding a fraction', () => {
    expect(formatMinor(123405)).toBe('$1,234.05')
    expect(formatMinor(0)).toBe('$0.00')
    expect(formatMinor(5)).toBe('$0.05')
    expect(formatMinor(100)).toBe('$1.00')
  })

  it('uses a real minus sign, and can be asked for an explicit plus', () => {
    expect(formatMinor(-6594)).toBe('−$65.94')
    expect(formatMinor(3406, { showSign: true })).toBe('+$34.06')
    expect(formatMinor(-6594, { showSign: true })).toBe('−$65.94')
  })

  it('respects currencies that have no minor unit', () => {
    // The engine reads this from java.util.Currency; the browser has to agree or ¥3,334 becomes
    // ¥33.34 on screen while the server insists it is right.
    expect(fractionDigits('JPY')).toBe(0)
    expect(formatMinor(3334, { currencyCode: 'JPY', symbol: '¥' })).toBe('¥3,334')
    expect(formatMinor(-3334, { currencyCode: 'JPY', symbol: '¥' })).toBe('−¥3,334')
  })

  it('respects currencies with three minor digits', () => {
    expect(formatMinor(1234567, { currencyCode: 'KWD', symbol: 'KD' })).toBe('KD1,234.567')
  })

  it('refuses anything that is not whole minor units', () => {
    // A float arriving here means somebody has divided somewhere they should not have, and
    // rendering it would hide that rather than surface it.
    expect(() => formatMinor(12.5)).toThrow(/whole minor units/)
    expect(() => formatMinor(0.1 + 0.2)).toThrow(/whole minor units/)
  })

  it('never loses a cent to floating point, across the whole range of a trip', () => {
    // 1..20000 cents: every value must split and reassemble exactly. This is the property the
    // engine holds on the server, checked again on the far side of the wire.
    for (let cents = 0; cents <= 20_000; cents++) {
      const parts = splitMinor(cents, 'AUD')
      const whole = Number(parts.whole.replace(/,/g, ''))
      expect(whole * 100 + Number(parts.fraction)).toBe(cents)
    }
  })

  it('is still exact at amounts far larger than any real trip', () => {
    // 90,071,992,547,409 cents — within 2^53, where integers are still exact. Well past any trip,
    // but it is the boundary the "integers are safe" reasoning rests on, so it is worth pinning.
    expect(formatMinor(90_071_992_547_409)).toBe('$900,719,925,474.09')
  })
})
