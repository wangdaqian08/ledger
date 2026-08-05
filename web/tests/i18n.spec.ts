import { describe, expect, it } from 'vitest'
import en from '../src/i18n/en'
import { resolveLocale } from '../src/i18n'
import zh from '../src/i18n/zh'

/** Flatten to dotted keys so two message trees can be compared as sets. */
function keysOf(messages: object, prefix = ''): string[] {
  return Object.entries(messages).flatMap(([key, value]) =>
    typeof value === 'object' && value !== null ? keysOf(value, `${prefix}${key}.`) : [`${prefix}${key}`],
  )
}

describe('i18n', () => {
  it('has the same keys in both languages', () => {
    // English is the source language, so a key here with no Chinese counterpart is a string that
    // will silently fall back and never be noticed. The Chinese *voice* is a later task; its
    // coverage is not.
    expect(keysOf(zh).sort()).toEqual(keysOf(en).sort())
  })

  it('carries the named placeholders through to the translation', () => {
    // A translation that drops {name} renders "Sent to for confirmation" and nobody finds out
    // until somebody reads it in Chinese.
    for (const key of keysOf(en)) {
      const english = key.split('.').reduce<never>((acc, part) => acc[part], en as never) as string
      const chinese = key.split('.').reduce<never>((acc, part) => acc[part], zh as never) as string
      const placeholders = (text: string) => (text.match(/\{[a-zA-Z]+\}/g) ?? []).sort()

      expect(placeholders(chinese), `placeholders differ for ${key}`).toEqual(placeholders(english))
    }
  })

  it('resolves a browser language to something it can actually show', () => {
    expect(resolveLocale('zh-Hans-CN')).toBe('zh')
    expect(resolveLocale('zh')).toBe('zh')
    expect(resolveLocale('en-AU')).toBe('en')
    expect(resolveLocale('fr-FR')).toBe('en')
    expect(resolveLocale(undefined)).toBe('en')
  })
})
