import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { COMMENT_MAX_CHARS, COMMENT_MAX_WORDS, countWords } from '@/lib/words'

interface Vector {
  text: string
  words: number
}

/**
 * Read from the server's own test resources rather than a copy. A copy would drift, which is the
 * exact failure this file exists to prevent — `CommentWordVectorsTest` checks the Kotlin against
 * this same file, so there is one artefact and two guardians.
 */
const vectors: Vector[] = JSON.parse(
  // Relative to `web/`, where vitest runs, exactly as split.spec.ts reads the engine's vectors.
  readFileSync(resolve(process.cwd(), '../server/src/test/resources/comment-word-vectors.json'), 'utf8'),
)

describe('countWords against the server', () => {
  it('reads a non-empty set of vectors', () => {
    // Guards the guard: an empty or unreadable file would make every case below vacuously pass.
    expect(vectors.length).toBeGreaterThanOrEqual(12)
  })

  for (const vector of vectors) {
    it(`counts ${JSON.stringify(vector.text)} as ${vector.words}`, () => {
      expect(countWords(vector.text)).toBe(vector.words)
    })
  }
})

describe('countWords', () => {
  it('counts nothing in nothing', () => {
    expect(countWords('')).toBe(0)
    expect(countWords('   ')).toBe(0)
    expect(countWords('\n\t  \n')).toBe(0)
  })

  it('counts one word as one, however it is padded', () => {
    expect(countWords('dinner')).toBe(1)
    expect(countWords('   dinner   ')).toBe(1)
  })

  it('treats every run of whitespace as one separator', () => {
    expect(countWords('a b')).toBe(2)
    expect(countWords('a     b')).toBe(2)
    expect(countWords('  split  the   bill  ')).toBe(3)
    expect(countWords('a\nb\tc\r\nd')).toBe(4)
  })

  it('counts exactly at, and one past, the comment limit', () => {
    const hundred = Array.from({ length: 100 }, (_, i) => `w${i}`).join(' ')
    expect(countWords(hundred)).toBe(COMMENT_MAX_WORDS)
    expect(countWords(`${hundred} more`)).toBe(COMMENT_MAX_WORDS + 1)
  })

  it('counts a spaceless Chinese sentence as one word — which is why there is a character cap', () => {
    // The whole reason the limit is two-sided: Chinese writes no spaces, so the word count alone
    // would let a wall of text through. 100 words bounds English; COMMENT_MAX_CHARS bounds this.
    expect(countWords('晚饭是我先垫付的记得还我')).toBe(1)
    expect(countWords('晚饭 是我先垫付的')).toBe(2)
    expect(COMMENT_MAX_CHARS).toBe(1200)
  })

  it('treats an ideographic space as a word gap, the way the server does', () => {
    // U+3000, the space a Chinese keyboard produces. JavaScript's \s matches it; Java's does not
    // unless asked, so the server pins this too — otherwise the counter here goes red on a comment
    // the API would accept.
    expect(countWords('晚饭　是我先垫付的　记得还我')).toBe(3)
  })
})
