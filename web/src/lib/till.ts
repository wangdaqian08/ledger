/**
 * Till-style amount entry: every key shifts the integer left and adds, delete shifts right. The
 * value is exact at every step and the decimal point is only ever presentation — the one way to
 * type money that cannot produce 19.989999999999998.
 *
 * This module owns both what keys exist and what they do, so every keypad in the app agrees.
 * There is deliberately no decimal-point key: offering one would invite somebody to type it and
 * expect a float.
 */
export const KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '00', '0', 'del'] as const

export type KeypadKey = (typeof KEYS)[number]

export function pressKey(currentMinor: number, key: KeypadKey): number {
  if (key === 'del') return Math.floor(currentMinor / 10)
  const shifted = currentMinor * 10 ** key.length + Number(key)
  // Refusing beats wrapping: past the safe range the digits would stop being exact.
  return shifted <= Number.MAX_SAFE_INTEGER ? shifted : currentMinor
}
