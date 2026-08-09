import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AmountInput from '../src/components/AmountInput.vue'
import AmountKeypadField from '../src/components/AmountKeypadField.vue'
import CategoryPicker from '../src/components/CategoryPicker.vue'
import PersonToggleRow from '../src/components/PersonToggleRow.vue'
import SplitBar from '../src/components/SplitBar.vue'
import { saltFor, splitShares } from '../src/lib/split'
import { pressKey } from '../src/lib/till'
import { findAllByTestId, findByTestId } from './testids'

const people = [
  { memberId: 'a', displayName: 'Bob', personHue: 1, weight: 2 },
  { memberId: 'b', displayName: 'Alice', personHue: 2, weight: 1 },
]

describe('SplitBar', () => {
  it('shows what each person will actually be charged, not a rounded percentage', () => {
    // 10001 over 2:1 is 6667.33 and 3333.67 — the sort of total where a percentage-based preview
    // and the server disagree by a cent.
    const salt = saltFor('01234567-89ab-cdef-0000-000000000000')
    const bar = mount(SplitBar, { props: { people, totalMinor: 10_001, salt } })

    const expected = splitShares({ totalMinor: 10_001, weights: [2, 1], salt })
    expect(expected.reduce((a, b) => a + b, 0)).toBe(10_001)
    for (const amount of expected) {
      expect(bar.text()).toContain((amount / 100).toFixed(2))
    }
  })

  it('a drag moves the weights — whole, above zero, and actually moved', async () => {
    // Scaled weights, the way the sheets now hand them over: at 1:1 every pair sums to 2 and the
    // clamp pins the handle in place — the bar shipped provably inert, and the old version of
    // this test tolerated silence (`if (emitted)`), which is how nobody noticed.
    const scaled = [
      { memberId: 'a', displayName: 'Bob', personHue: 1, weight: 40 },
      { memberId: 'b', displayName: 'Alice', personHue: 2, weight: 20 },
    ]
    const bar = mount(SplitBar, { props: { people: scaled, totalMinor: 10_000, salt: 0n } })
    // happy-dom boxes are zero-sized; the drag arithmetic needs a real-shaped bar.
    const barBox = findByTestId(bar, 'split-bar').element as HTMLElement
    barBox.getBoundingClientRect = () =>
      ({ left: 0, width: 300, top: 0, height: 56, right: 300, bottom: 56, x: 0, y: 0 }) as DOMRect

    await findByTestId(bar, 'split-handle').trigger('pointerdown')
    // A third of the way across a 60-weight bar: the pair re-divides to 20:40.
    await bar.find('.split').trigger('pointermove', { clientX: 100 })

    const emitted = bar.emitted('update:people')
    expect(emitted, 'a drag must emit — silence is the bug').toBeTruthy()
    const [next] = emitted!.at(-1) as [typeof scaled]
    expect(next.map((p) => p.weight)).toEqual([20, 40])
    for (const person of next) {
      expect(Number.isInteger(person.weight)).toBe(true)
      // Nobody is ever dragged to nothing: zero is "not sharing this", which is a different
      // statement and belongs to the tick, not the bar.
      expect(person.weight).toBeGreaterThanOrEqual(1)
    }
  })

  it('renders one handle fewer than there are people', () => {
    const three = [...people, { memberId: 'c', displayName: 'Carol', personHue: 3, weight: 1 }]
    const bar = mount(SplitBar, { props: { people: three, totalMinor: 9_000, salt: 0n } })
    expect(findAllByTestId(bar, 'split-handle')).toHaveLength(2)
  })

  it('holds still beside a zero-weight person instead of snatching the whole weight across', async () => {
    // A pair holding one unit between them cannot be re-divided with both kept above zero. The
    // clamp used to answer 0 here — the first pixel of drag flipped the entire weight to the
    // other person, committing a $0 share for somebody who should have kept everything.
    const zeroNeighbour = [
      { memberId: 'a', displayName: 'Bob', personHue: 1, weight: 1 },
      { memberId: 'b', displayName: 'Alice', personHue: 2, weight: 0 },
    ]
    const bar = mount(SplitBar, { props: { people: zeroNeighbour, totalMinor: 10_000, salt: 0n } })

    await findByTestId(bar, 'split-handle').trigger('pointerdown')
    await bar.find('.split').trigger('pointermove', { clientX: 1 })

    expect(bar.emitted('update:people')).toBeUndefined()
  })
})

describe('AmountInput', () => {
  it('accumulates digits as whole cents, like a till', async () => {
    const input = mount(AmountInput, { props: { modelValue: 0 } })

    await input.find('input').setValue('1999')

    // 19.99 typed as digits is 1999 cents. Never 19.99 the float, which is 19.989999999999998.
    expect(input.emitted('update:modelValue')?.at(-1)).toEqual([1999])
    expect((input.find('input').element as HTMLInputElement).value).toBe('19.99')
  })

  it('ignores anything that is not a digit', async () => {
    const input = mount(AmountInput, { props: { modelValue: 0 } })

    await input.find('input').setValue('1a9$9.9')

    expect(input.emitted('update:modelValue')?.at(-1)).toEqual([1999])
  })

  it('has no decimal point at all for a zero-decimal currency', async () => {
    const input = mount(AmountInput, { props: { modelValue: 0, currencyCode: 'JPY', symbol: '¥' } })

    await input.find('input').setValue('3334')

    expect(input.emitted('update:modelValue')?.at(-1)).toEqual([3334])
    expect((input.find('input').element as HTMLInputElement).value).toBe('3334')
  })

  it('shows an empty field rather than a zero, so the placeholder can do its job', () => {
    const input = mount(AmountInput, { props: { modelValue: 0 } })
    expect((input.find('input').element as HTMLInputElement).value).toBe('')
  })

  it('clears a typed zero from the screen, not only from the model', async () => {
    // The browser paints the keystroke before Vue hears about it, and a ref set to the value it
    // already holds patches nothing — so a typed "0" used to stay visible against a model of 0.
    const input = mount(AmountInput, { props: { modelValue: 0 } })

    await input.find('input').setValue('0')

    expect(input.emitted('update:modelValue')?.at(-1)).toEqual([0])
    expect((input.find('input').element as HTMLInputElement).value).toBe('')
  })

  it('rejects a digit past the safe-integer cap on screen as well as in the model', async () => {
    const input = mount(AmountInput, { props: { modelValue: 9_007_199_254_740_991 } })

    await input.find('input').setValue('90071992547409919')

    // Nothing was emitted, and the field snapped back to the last accepted amount rather than
    // keeping the rejected text.
    expect(input.emitted('update:modelValue')).toBeUndefined()
    expect((input.find('input').element as HTMLInputElement).value).toBe('90071992547409.91')
  })

  it('moves the decimal point when the currency changes under an unchanged amount', async () => {
    const input = mount(AmountInput, { props: { modelValue: 1999 } })
    expect((input.find('input').element as HTMLInputElement).value).toBe('19.99')

    await input.setProps({ currencyCode: 'JPY', symbol: '¥' })

    // Same digits, different currency: 1999 minor units of yen are ¥1999, not ¥19.99.
    expect((input.find('input').element as HTMLInputElement).value).toBe('1999')
  })
})

describe('till', () => {
  it('shifts digits like a drawer and refuses to leave the safe range', () => {
    expect(pressKey(0, '4')).toBe(4)
    expect(pressKey(4, '2')).toBe(42)
    expect(pressKey(42, '00')).toBe(4200)
    expect(pressKey(4200, 'del')).toBe(420)
    // One keypress past Number.MAX_SAFE_INTEGER changes nothing rather than going inexact.
    expect(pressKey(Number.MAX_SAFE_INTEGER, '9')).toBe(Number.MAX_SAFE_INTEGER)
  })
})

describe('AmountKeypadField', () => {
  it('unfolds its keypad on tap and folds it again', async () => {
    const field = mount(AmountKeypadField, { props: { modelValue: 2_500, testId: 'amt' } })
    expect(field.text()).toContain('25.00')
    expect(findByTestId(field, 'key-1').exists()).toBe(false)

    await findByTestId(field, 'amt').trigger('click')
    expect(findByTestId(field, 'key-1').exists()).toBe(true)

    await findByTestId(field, 'amt').trigger('click')
    expect(findByTestId(field, 'key-1').exists()).toBe(false)
  })

  it('keys shift cents through the shared till, delete included', async () => {
    const field = mount(AmountKeypadField, {
      props: { modelValue: 0, testId: 'amt', startOpen: true },
    })

    await findByTestId(field, 'key-4').trigger('click')
    // The parent owns the model; feed the emitted value back like v-model would.
    await field.setProps({ modelValue: field.emitted('update:modelValue')!.at(-1)![0] as number })
    await findByTestId(field, 'key-2').trigger('click')
    await field.setProps({ modelValue: field.emitted('update:modelValue')!.at(-1)![0] as number })
    expect(field.props('modelValue')).toBe(42)

    await findByTestId(field, 'key-del').trigger('click')
    expect(field.emitted('update:modelValue')!.at(-1)![0]).toBe(4)
  })
})

describe('CategoryPicker', () => {
  const category = (n: number) => ({
    id: `c-${n}`,
    key: `k${n}`,
    nameEn: `Cat ${n}`,
    nameZh: `类${n}`,
    icon: 'utensils',
    hue: ((n - 1) % 8) + 1,
    builtIn: true,
  })

  it('keeps eight tiles on one page with no dots at all', () => {
    const picker = mount(CategoryPicker, {
      props: { categories: Array.from({ length: 8 }, (_, i) => category(i + 1)), modelValue: null },
    })
    expect(findAllByTestId(picker, 'category-item')).toHaveLength(8)
    expect(findByTestId(picker, 'category-dots').exists()).toBe(false)
  })

  it('pages past eight, with a dot per page that reports and steers', async () => {
    const picker = mount(CategoryPicker, {
      props: { categories: Array.from({ length: 9 }, (_, i) => category(i + 1)), modelValue: null },
    })

    const dots = findAllByTestId(picker, 'category-dot')
    expect(dots).toHaveLength(2)
    expect(dots[0]!.attributes('aria-current')).toBe('true')

    await dots[1]!.trigger('click')
    expect(findAllByTestId(picker, 'category-dot')[1]!.attributes('aria-current')).toBe('true')
  })

  it('marks the chosen tile with the grape wash', async () => {
    const picker = mount(CategoryPicker, {
      props: { categories: [category(1), category(2)], modelValue: 'c-2' },
    })
    const chosen = findAllByTestId(picker, 'category-item')[1]!
    expect(chosen.classes()).toContain('picker__item--on')
    expect(chosen.attributes('aria-checked')).toBe('true')
  })
})

describe('PersonToggleRow', () => {
  it('toggles both ways', async () => {
    const on = mount(PersonToggleRow, {
      props: { displayName: 'Jack', personHue: 5, selected: false },
    })
    await on.trigger('click')
    expect(on.emitted('update:selected')?.[0]).toEqual([true])

    const off = mount(PersonToggleRow, {
      props: { displayName: 'Jack', personHue: 5, selected: true },
    })
    await off.trigger('click')
    expect(off.emitted('update:selected')?.[0]).toEqual([false])
  })

  it('shows a share only once there is one to show', () => {
    const without = mount(PersonToggleRow, {
      props: { displayName: 'Jack', personHue: 5, selected: true },
    })
    expect(without.text()).not.toContain('$')

    const with_ = mount(PersonToggleRow, {
      props: { displayName: 'Jack', personHue: 5, selected: true, shareMinor: 14286 },
    })
    expect(with_.text()).toContain('$142.86')
  })
})
