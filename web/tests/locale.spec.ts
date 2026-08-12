import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import LocaleToggle from '../src/components/LocaleToggle.vue'
import { i18n, setLocale } from '../src/i18n'
import { findByTestId } from './testids'

describe('LocaleToggle', () => {
  beforeEach(() => {
    // This Node's built-in localStorage has no working methods, so stand in a real in-memory one —
    // then "remembers the choice" tests the actual write, not the host's quirk.
    const store = new Map<string, string>()
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => void store.set(key, value),
      removeItem: (key: string) => void store.delete(key),
      clear: () => store.clear(),
    })
  })

  afterEach(() => {
    // The suite shares one i18n instance; leaving it on zh would make every English assertion that
    // runs after this file fail. Put it back, and drop the stubbed storage.
    setLocale('en')
    vi.unstubAllGlobals()
  })

  it('lights the language in force and offers the other', () => {
    const toggle = mount(LocaleToggle)
    expect(findByTestId(toggle, 'locale-en').attributes('aria-pressed')).toBe('true')
    expect(findByTestId(toggle, 'locale-zh').attributes('aria-pressed')).toBe('false')
    expect(toggle.text()).toContain('EN')
    expect(toggle.text()).toContain('中文')
  })

  it('switches the whole app and remembers the choice', async () => {
    const toggle = mount(LocaleToggle)
    await findByTestId(toggle, 'locale-zh').trigger('click')

    expect(i18n.global.locale.value).toBe('zh')
    expect(localStorage.getItem('ledger.locale')).toBe('zh')
    // The lit segment follows the switch.
    expect(findByTestId(toggle, 'locale-zh').attributes('aria-pressed')).toBe('true')
    expect(findByTestId(toggle, 'locale-en').attributes('aria-pressed')).toBe('false')
  })
})
