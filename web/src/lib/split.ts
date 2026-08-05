/**
 * Largest remainder, in the browser — a second implementation of the one rule this application
 * cannot afford to get wrong.
 *
 * It exists because the SplitBar shows each person's amount live as it is dragged, before anything
 * has been saved. Predicting the server's answer means predicting which member absorbs each spare
 * cent, which depends on the item's id; that is why the client mints the id up front.
 *
 * This is a deliberate duplication of `engine/src/main/kotlin/app/ledger/engine/Split.kt`, and the
 * two are pinned to each other by `split-vectors.json`: the engine regenerates and checks it, and
 * `tests/split.spec.ts` checks this port against the same file. Neither can move without a red
 * test. **If you change anything here, change Split.kt too, or the number somebody sees while
 * dragging will not be the number they are charged.**
 */

/** Beyond this, `total * weight` stops being exact in a double and the arithmetic silently rots. */
const MAX_EXACT = Number.MAX_SAFE_INTEGER

export interface SplitInput {
  totalMinor: number
  /** One weight per person, in the order they appear on the item. Equal split is every weight 1. */
  weights: number[]
  /**
   * The item's id as a 64-bit value. A bigint because that is what it is on the server — reducing
   * it to a double first would change `salt % count` for some member counts and not others, which
   * is the worst kind of wrong.
   */
  salt: bigint
}

export function splitShares({ totalMinor, weights, salt }: SplitInput): number[] {
  const count = weights.length
  if (count === 0) throw new Error('cannot split between no one')
  if (!Number.isInteger(totalMinor)) throw new Error(`total must be whole minor units, got ${totalMinor}`)
  if (weights.some((w) => !Number.isInteger(w) || w < 0)) {
    throw new Error('every weight must be a whole number that is not negative')
  }

  const totalWeight = weights.reduce((a, b) => a + b, 0)
  if (totalWeight === 0) throw new Error('at least one person must carry some weight')
  if (Math.abs(totalMinor) * Math.max(...weights) > MAX_EXACT) {
    // Refusing beats quietly returning a number that is a cent or two out.
    throw new Error('amount and weights are too large to split exactly')
  }

  // Done in bigint and only then narrowed: `count` is small, so the result always fits, but the
  // salt itself does not. Kotlin's `%` keeps the sign of the salt and so does bigint's, which is
  // what makes the rotation below agree with the engine for negative salts too.
  const offset = Number(salt % BigInt(count))

  const parts = weights.map((weight, index) => {
    const numerator = totalMinor * weight
    return {
      index,
      base: Math.floor(numerator / totalWeight),
      fraction: numerator % totalWeight,
      rotated: (((index - offset) % count) + count) % count,
    }
  })

  const distributed = parts.reduce((sum, part) => sum + part.base, 0)
  const leftover = totalMinor - distributed

  // Biggest fractional part first; ties broken by position rotated by the salt, so the spare cent
  // lands on different people across a trip rather than always the first name on the list.
  const winners = new Set(
    [...parts]
      .sort((a, b) => b.fraction - a.fraction || a.rotated - b.rotated)
      .slice(0, leftover)
      .map((part) => part.index),
  )

  return parts.map((part) => part.base + (winners.has(part.index) ? 1 : 0))
}

/**
 * The salt for an item, from its UUID — the browser's copy of `engineItemId` on the server.
 *
 * Both halves are folded together with XOR. Taking only the high 64 bits, as this once did, means
 * two ids differing solely in their low bits share a salt — and on the server, an identity. That
 * is exactly what UUIDv7 produces for two items created in the same millisecond.
 */
export function saltFor(itemId: string): bigint {
  const hex = itemId.replace(/-/g, '')
  if (!/^[0-9a-fA-F]{32}$/.test(hex)) throw new Error(`not a uuid: ${itemId}`)

  const unsigned = BigInt(`0x${hex.slice(0, 16)}`) ^ BigInt(`0x${hex.slice(16)}`)
  // Two's complement, exactly as Java reads a Long.
  return unsigned >= 1n << 63n ? unsigned - (1n << 64n) : unsigned
}

/** A v4 UUID for a new item, so its salt — and therefore its rounding — is known before saving. */
export function newItemId(): string {
  return crypto.randomUUID()
}
