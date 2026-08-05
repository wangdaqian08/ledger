import { createI18n } from 'vue-i18n'
import en from './en'
import zh from './zh'

export const SUPPORTED_LOCALES = ['en', 'zh'] as const
export type Locale = (typeof SUPPORTED_LOCALES)[number]

export function resolveLocale(candidate: string | undefined): Locale {
  // "zh-Hans-CN" and "zh-TW" alike should land on zh; anything unrecognised on English.
  const base = candidate?.split('-')[0]?.toLowerCase()
  return SUPPORTED_LOCALES.includes(base as Locale) ? (base as Locale) : 'en'
}

export const i18n = createI18n({
  // Composition API mode. `legacy: false` is not a detail — the Options API mode is a different
  // library with different behaviour, and mixing them is how `$t` starts returning keys.
  legacy: false,
  locale: resolveLocale(typeof navigator === 'undefined' ? undefined : navigator.language),
  fallbackLocale: 'en',
  messages: { en, zh },
})
