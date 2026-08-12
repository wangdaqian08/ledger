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

const STORAGE_KEY = 'ledger.locale'

/** A locale the viewer chose on a previous visit, if it is still one we ship. */
function storedLocale(): Locale | undefined {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    return saved && SUPPORTED_LOCALES.includes(saved as Locale) ? (saved as Locale) : undefined
  } catch {
    // No storage, or a host that exposes it without working methods (some test runtimes): fall back
    // to the browser language. The switch still works this session; it just is not remembered.
    return undefined
  }
}

export const i18n = createI18n({
  // Composition API mode. `legacy: false` is not a detail — the Options API mode is a different
  // library with different behaviour, and mixing them is how `$t` starts returning keys.
  legacy: false,
  // A remembered choice wins over the browser's language: someone who switched to 中文 last time
  // meant it, and should not have to switch again every visit.
  locale: storedLocale() ?? resolveLocale(typeof navigator === 'undefined' ? undefined : navigator.language),
  fallbackLocale: 'en',
  messages: { en, zh },
})

/** Switch the whole app's language and remember it. The one place the locale is written. */
export function setLocale(locale: Locale): void {
  i18n.global.locale.value = locale
  try {
    localStorage.setItem(STORAGE_KEY, locale)
  } catch {
    // Remembering is best-effort: private-mode, disabled storage, or a stubbed test host all just
    // mean the switch does not survive a reload.
  }
  // Keep the document's own language in step, for the lang attribute screen readers and CSS read.
  if (typeof document !== 'undefined') document.documentElement.lang = locale
}
