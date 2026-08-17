import { describe, expect, it } from 'vitest'
import { fitWithin } from '../src/lib/receipt'

/**
 * The downscale target arithmetic. The canvas pipeline itself needs a real browser (happy-dom
 * decodes nothing) and is exercised by the e2e suite; the sizing rule is pure and pinned here.
 */
describe('fitWithin', () => {
  it('leaves an image already inside the limit untouched', () => {
    expect(fitWithin(800, 600, 1600)).toEqual({ width: 800, height: 600 })
  })

  it('shrinks a landscape photo by its longest edge, keeping the aspect', () => {
    expect(fitWithin(4000, 3000, 1600)).toEqual({ width: 1600, height: 1200 })
  })

  it('shrinks a portrait photo the same way round', () => {
    expect(fitWithin(3000, 4000, 1600)).toEqual({ width: 1200, height: 1600 })
  })

  it('never rounds a sliver of an image down to nothing', () => {
    expect(fitWithin(10_000, 1, 1600)).toEqual({ width: 1600, height: 1 })
  })
})
