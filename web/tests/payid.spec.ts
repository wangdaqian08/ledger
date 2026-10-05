import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import CopyButton from '../src/components/CopyButton.vue'
import PayIdLine from '../src/components/PayIdLine.vue'
import { findAllByTestId, findByTestId } from './testids'

/**
 * PayID is shown, never used: Tally moves no money, so the whole feature is a value you can read
 * and a button that puts it on your clipboard. These pin the two pieces every PayID surface reuses.
 */

function stubClipboard(writeText: (text: string) => Promise<void>) {
  vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText } })
}

describe('CopyButton', () => {
  beforeEach(() => {
    // Only the timers the label flip uses: flushPromises leans on setImmediate, which must stay real.
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('copies the text, says so in words a screen reader hears, then goes back to Copy', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    stubClipboard(writeText)
    const button = mount(CopyButton, {
      props: { text: 'cat@example.com', label: "Copy Cat's PayID", testId: 'payid-copy' },
    })
    const control = findByTestId(button, 'payid-copy')
    expect(control.attributes('aria-label')).toBe("Copy Cat's PayID")
    expect(control.text()).toContain('Copy')

    await control.trigger('click')
    await flushPromises()

    expect(writeText).toHaveBeenCalledWith('cat@example.com')
    expect(control.text()).toContain('Copied')
    // The visible label flipping is not news to somebody who cannot see it; the live region is.
    expect(button.find('[aria-live="polite"]').text()).toContain('Copied')

    vi.advanceTimersByTime(2_000)
    await nextTick()
    expect(control.text()).not.toContain('Copied')
    expect(control.text()).toContain('Copy')
    expect(button.find('[aria-live="polite"]').text()).toBe('')
  })

  it('does nothing alarming when the clipboard refuses — the value is right there to read', async () => {
    stubClipboard(() => Promise.reject(new Error('NotAllowedError')))
    const button = mount(CopyButton, {
      props: { text: 'cat@example.com', label: "Copy Cat's PayID", testId: 'payid-copy' },
    })

    await findByTestId(button, 'payid-copy').trigger('click')
    await flushPromises()

    expect(button.text()).not.toContain('Copied')
    expect(findByTestId(button, 'payid-copy').attributes('disabled')).toBeUndefined()
  })

  it('survives a browser with no clipboard at all (an insecure origin)', async () => {
    vi.stubGlobal('navigator', { ...navigator, clipboard: undefined })
    const button = mount(CopyButton, {
      props: { text: 'cat@example.com', label: "Copy Cat's PayID", testId: 'payid-copy' },
    })

    await findByTestId(button, 'payid-copy').trigger('click')
    await flushPromises()

    expect(button.text()).not.toContain('Copied')
  })
})

describe('PayIdLine', () => {
  const base = { payId: 'cat@example.com', recentlyChanged: false, ownerName: 'Cat' }

  it('shows the PayID exactly as entered, with a Copy named for whose it is', () => {
    const line = mount(PayIdLine, { props: base })
    expect(line.text()).toContain('PayID')
    expect(findByTestId(line, 'payid-value').text()).toBe('cat@example.com')
    expect(findByTestId(line, 'payid-copy').attributes('aria-label')).toBe("Copy Cat's PayID")
    expect(findAllByTestId(line, 'payid-none')).toHaveLength(0)
  })

  it('names whose PayID it is when asked — a family has several people it could belong to', () => {
    const named = mount(PayIdLine, { props: { ...base, showOwner: true } })
    expect(named.text()).toContain("Cat's PayID")

    const plain = mount(PayIdLine, { props: base })
    expect(plain.text()).not.toContain("Cat's PayID")
  })

  it('copies the PayID itself — the value pasted into a banking app', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    stubClipboard(writeText)
    const line = mount(PayIdLine, { props: base })

    await findByTestId(line, 'payid-copy').trigger('click')
    await flushPromises()

    expect(writeText).toHaveBeenCalledWith('cat@example.com')
    vi.unstubAllGlobals()
  })

  it('says plainly when there is no PayID, and offers nothing to copy', () => {
    const line = mount(PayIdLine, { props: { ...base, payId: null } })
    expect(findByTestId(line, 'payid-none').text()).toBe('(no PayID provided)')
    expect(findAllByTestId(line, 'payid-copy')).toHaveLength(0)
    expect(findAllByTestId(line, 'payid-value')).toHaveLength(0)
  })

  it('flags a PayID changed recently, and only then', () => {
    // Sign-in is a bare name, so a swapped PayID is how an impersonator would redirect money; the
    // badge is how the others get to notice before they pay it.
    const recent = mount(PayIdLine, { props: { ...base, recentlyChanged: true } })
    expect(findByTestId(recent, 'payid-recent').text()).toBe('Updated recently')

    const settled = mount(PayIdLine, { props: base })
    expect(findAllByTestId(settled, 'payid-recent')).toHaveLength(0)
  })

  it('renders markup in a PayID as the literal characters, never as HTML', () => {
    const line = mount(PayIdLine, { props: { ...base, payId: '<b>x</b>' } })
    expect(findByTestId(line, 'payid-value').text()).toBe('<b>x</b>')
    expect(line.find('b').exists()).toBe(false)
  })

  it('can drop Copy for your own PayID and carry an action of the caller instead', () => {
    const line = mount(PayIdLine, {
      props: { ...base, copyable: false },
      slots: { default: '<button data-testid="payid-edit">edit</button>' },
    })
    expect(findByTestId(line, 'payid-value').text()).toBe('cat@example.com')
    expect(findAllByTestId(line, 'payid-copy')).toHaveLength(0)
    expect(findByTestId(line, 'payid-edit').exists()).toBe(true)
  })

  it('carries the caller test id on its root so a row can be scoped to it', () => {
    const line = mount(PayIdLine, { props: { ...base, testId: 'transfer-payid' } })
    expect(line.attributes('data-testid')).toBe('transfer-payid')
  })
})
