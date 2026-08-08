/**
 * Weights are integers, and that nearly killed the SplitBar: with everyone at weight 1, every
 * adjacent pair sums to 2, and a drag clamped to keep both sides above zero has exactly one
 * reachable state — the one it is already in. The bar was provably inert, not merely stiff.
 *
 * The cure is resolution: entering custom mode multiplies every weight by [DRAG_SCALE], which
 * changes no ratio (largest remainder is exact under common scaling — quotas, floors and
 * tie-breaks all survive) but gives the handle DRAG_SCALE positions per person to move through.
 * Saving divides the greatest common divisor back out, so a bar dragged to 40:20 is stored as
 * the 2:1 it means.
 */
export const DRAG_SCALE = 20

/** The weights with their greatest common divisor divided out: 40:20 → 2:1, 37:23 → 37:23. */
export function normalizedWeights(weights: number[]): number[] {
  const gcd = weights.reduce((a, b) => {
    let [x, y] = [a, b]
    while (y !== 0) [x, y] = [y, x % y]
    return x
  })
  return gcd > 1 ? weights.map((w) => w / gcd) : weights
}
