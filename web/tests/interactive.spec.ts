import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AmountInput from '../src/components/AmountInput.vue'
import PersonToggleRow from '../src/components/PersonToggleRow.vue'
import SplitBar from '../src/components/SplitBar.vue'
import { saltFor, splitShares } from '../src/lib/split'

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

  it('keeps weights whole when dragged', async () => {
    const bar = mount(SplitBar, { props: { people, totalMinor: 10_000, salt: 0n } })
    const handle = bar.find('.split__handle-hit')

    await handle.trigger('pointerdown')
    await bar.find('.split').trigger('pointermove', { clientX: 40 })

    const emitted = bar.emitted('update:people')
    if (emitted) {
      for (const [next] of emitted as [typeof people][]) {
        for (const person of next) {
          expect(Number.isInteger(person.weight)).toBe(true)
          // Nobody is ever dragged to nothing: zero is "not sharing this", which is a different
          // statement and belongs to the tick, not the bar.
          expect(person.weight).toBeGreaterThanOrEqual(1)
        }
      }
    }
  })

  it('renders one handle fewer than there are people', () => {
    const three = [...people, { memberId: 'c', displayName: 'Carol', personHue: 3, weight: 1 }]
    const bar = mount(SplitBar, { props: { people: three, totalMinor: 9_000, salt: 0n } })
    expect(bar.findAll('.split__handle-hit')).toHaveLength(2)
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
