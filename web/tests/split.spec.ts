import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { saltFor, splitShares } from '../src/lib/split'

interface Vector {
  totalMinor: number
  weights: number[]
  salt: number
  equal: boolean
  shares: number[]
}

/**
 * Read from the engine's own test resources rather than a copy. A copy would drift, which is the
 * exact failure this file exists to prevent — the engine regenerates and checks this same file, so
 * there is one artefact and two guardians.
 */
const vectors: Vector[] = JSON.parse(
  // Relative to `web/`, where vitest runs. `import.meta.url` is not a file URL under the DOM
  // environment these tests use, so it cannot be resolved from there.
  readFileSync(resolve(process.cwd(), '../engine/src/test/resources/split-vectors.json'), 'utf8'),
)

describe('splitShares against the engine', () => {
  it('reads a non-empty set of vectors', () => {
    // Guards the guard: an empty or unreadable file would make every case below vacuously pass.
    expect(vectors.length).toBeGreaterThan(10)
  })

  it.each(vectors)(
    'matches the engine for total $totalMinor, salt $salt, weights $weights',
    ({ totalMinor, weights, salt, shares }) => {
      expect(splitShares({ totalMinor, weights, salt: BigInt(salt) })).toEqual(shares)
    },
  )

  it('never loses or invents a cent', () => {
    for (const vector of vectors) {
      const parts = splitShares({
        totalMinor: vector.totalMinor,
        weights: vector.weights,
        salt: BigInt(vector.salt),
      })
      expect(parts.reduce((a, b) => a + b, 0)).toBe(vector.totalMinor)
    }
  })

  it('never hands a spare cent to somebody carrying no weight', () => {
    // Somebody on the item at zero weight owes nothing, and a stray cent would be a debt the
    // person never agreed to.
    const parts = splitShares({ totalMinor: 100, weights: [1, 0, 1], salt: 0n })
    expect(parts[1]).toBe(0)
  })

  it('refuses input it cannot split exactly', () => {
    expect(() => splitShares({ totalMinor: 100, weights: [], salt: 0n })).toThrow(/no one/)
    expect(() => splitShares({ totalMinor: 10.5, weights: [1], salt: 0n })).toThrow(/whole minor units/)
    expect(() => splitShares({ totalMinor: 100, weights: [0, 0], salt: 0n })).toThrow(/some weight/)
    expect(() => splitShares({ totalMinor: 100, weights: [-1, 2], salt: 0n })).toThrow(/not negative/)
  })
})

describe('saltFor', () => {
  it('reads the top 64 bits the way Java does, including the sign', () => {
    // Java's UUID.getMostSignificantBits is signed, so anything with the top bit set is negative.
    expect(saltFor('ffffffff-ffff-ffff-0000-000000000000')).toBe(-1n)
    expect(saltFor('00000000-0000-0001-0000-000000000000')).toBe(1n)
    expect(saltFor('80000000-0000-0000-0000-000000000000')).toBe(-(2n ** 63n))
    expect(saltFor('7fffffff-ffff-ffff-0000-000000000000')).toBe(2n ** 63n - 1n)
  })

  it('is exact for member counts that no reduced salt would survive', () => {
    // A salt narrowed to a double, or reduced modulo some convenient number, agrees with the
    // server for small groups and quietly disagrees for others. 17 is the first count that catches
    // the obvious shortcut.
    const salt = saltFor('01234567-89ab-cdef-0000-000000000000')
    expect(salt).toBe(0x0123456789abcdefn)
    expect(Number(salt % 17n)).toBe(Number(0x0123456789abcdefn % 17n))
  })

  it('folds in the low bits, so ids differing only there differ here too', () => {
    // The server matches paybacks to items by this value. When it used only the high 64 bits,
    // these two were one item and one bill's repayments settled the other. UUIDv7 makes that the
    // normal case for two expenses added in the same millisecond.
    const first = saltFor('cccccccc-0000-4000-8000-000000000001')
    const second = saltFor('cccccccc-0000-4000-8000-000000000002')

    expect(first).not.toBe(second)
    expect(saltFor('00000000-0000-0000-0000-000000000005')).toBe(5n)
  })

  it('rejects anything that is not a uuid', () => {
    expect(() => saltFor('not-a-uuid')).toThrow(/not a uuid/)
    // A truncated id used to be silently accepted, because only the first 16 hex digits were read.
    expect(() => saltFor('cccccccc-0000-4000-8000')).toThrow(/not a uuid/)
  })
})
