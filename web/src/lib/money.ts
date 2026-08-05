/**
 * Money, in the browser, on the same terms as everywhere else: integer minor units.
 *
 * Tally's own `Amount` takes a major-unit float and calls `toLocaleString`. That was not ported.
 * Every amount crossing the API is named `*Minor` and is a whole number of cents, and turning it
 * into a float to display it would reintroduce — at the very last step, where nobody looks — the
 * one class of bug this application exists to avoid.
 *
 * JavaScript numbers are doubles, but every integer below 2^53 is exact, which is about ninety
 * trillion cents. The rule here is simply that no fractional value is ever computed: the split
 * into whole and fractional parts is done with `%` and an exact division, never with `/ 100`
 * followed by rounding.
 */

/** Minor units per major unit, by ISO-4217 code. Anything unlisted has the usual two. */
const FRACTION_DIGITS: Record<string, number> = {
  JPY: 0,
  KRW: 0,
  VND: 0,
  CLP: 0,
  ISK: 0,
  BHD: 3,
  JOD: 3,
  KWD: 3,
  OMR: 3,
  TND: 3,
}

export function fractionDigits(currencyCode: string): number {
  return FRACTION_DIGITS[currencyCode.toUpperCase()] ?? 2
}

export interface MoneyParts {
  /** True when the underlying amount is below zero, whatever the formatted text says. */
  negative: boolean
  /** The whole part, grouped for the locale: "1,234". */
  whole: string
  /** The fractional part with no separator: "05". Empty for zero-decimal currencies. */
  fraction: string
}

/**
 * Split a minor-unit amount into the pieces a display needs, without ever holding a fraction.
 *
 * `abs - (abs % scale)` is exactly divisible by `scale`, so the division that follows is exact for
 * every value in range. Doing `abs / scale` and rounding would be correct for realistic amounts and
 * wrong in a way nobody would ever catch by looking.
 */
export function splitMinor(amountMinor: number, currencyCode: string, locale = 'en-US'): MoneyParts {
  if (!Number.isInteger(amountMinor)) {
    throw new Error(`money must be whole minor units, got ${amountMinor}`)
  }

  const digits = fractionDigits(currencyCode)
  const scale = 10 ** digits
  const abs = Math.abs(amountMinor)
  const remainder = abs % scale
  const whole = (abs - remainder) / scale

  return {
    negative: amountMinor < 0,
    whole: whole.toLocaleString(locale, { useGrouping: true, maximumFractionDigits: 0 }),
    fraction: digits === 0 ? '' : String(remainder).padStart(digits, '0'),
  }
}

export interface FormatOptions {
  currencyCode?: string
  /** The symbol to show. The API gives an ISO code; picking a glyph for it is the caller's call. */
  symbol?: string
  /** Show a leading + or −. Off by default, because colour usually carries the sign. */
  showSign?: boolean
  locale?: string
}

/** The whole thing as one string: `−$1,234.05`. Uses a real minus sign, not a hyphen. */
export function formatMinor(amountMinor: number, options: FormatOptions = {}): string {
  const { currencyCode = 'AUD', symbol = '$', showSign = false, locale = 'en-US' } = options
  const parts = splitMinor(amountMinor, currencyCode, locale)

  const sign = showSign ? (parts.negative ? '−' : '+') : parts.negative ? '−' : ''
  const decimal = parts.fraction === '' ? '' : `.${parts.fraction}`

  return `${sign}${symbol}${parts.whole}${decimal}`
}
