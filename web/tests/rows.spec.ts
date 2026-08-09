import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AppBar from '../src/components/AppBar.vue'
import BalanceRow from '../src/components/BalanceRow.vue'
import ExpenseRow from '../src/components/ExpenseRow.vue'
import GroupCard from '../src/components/GroupCard.vue'
import ProgressBar from '../src/components/ProgressBar.vue'
import TallyIcon from '../src/components/TallyIcon.vue'
import TallyStepper from '../src/components/TallyStepper.vue'
import TallyKeypad from '../src/components/TallyKeypad.vue'
import { findByTestId } from './testids'

describe('ExpenseRow', () => {
  const base = { title: 'Hotel', yourShareMinor: -14286, categoryKey: 'stay' }

  it('says which way the money goes, in whole cents', () => {
    const row = mount(ExpenseRow, { props: base })
    expect(row.text()).toContain('$142.86')
    expect(row.text()).toContain('you owe')
  })

  it('reads settled from the item state, not from the share being zero', () => {
    // An item is square when every sharer's approved paybacks cover their portion. Somebody whose
    // own share happens to be nil is a different thing entirely, and must not read as settled.
    const square = mount(ExpenseRow, { props: { ...base, allSquare: true } })
    expect(square.text()).toContain('settled')

    const zeroShare = mount(ExpenseRow, { props: { ...base, yourShareMinor: 0 } })
    expect(zeroShare.text()).not.toContain('settled')
  })

  it('falls back to the other category rather than rendering nothing', () => {
    const row = mount(ExpenseRow, { props: { ...base, categoryKey: 'nonsense' } })
    expect(row.findComponent(TallyIcon).props('name')).toBe('circle-dashed')
  })
})

describe('BalanceRow', () => {
  const base = { displayName: 'Mei', personHue: 4 }

  it('offers Pay to somebody you owe and Remind to somebody who owes you', () => {
    const youOwe = mount(BalanceRow, { props: { ...base, owedMinor: -3910 } })
    expect(youOwe.text()).toContain('You owe')
    expect(youOwe.text()).toContain('Pay')
    expect(youOwe.text()).not.toContain('Remind')

    const theyOwe = mount(BalanceRow, { props: { ...base, owedMinor: 4230 } })
    expect(theyOwe.text()).toContain('Owes you')
    expect(theyOwe.text()).toContain('Remind')

    // A sent nudge changes the button itself — no extra row appears anywhere.
    const nudged = mount(BalanceRow, { props: { ...base, owedMinor: 4230, reminded: true } })
    expect(nudged.text()).toContain('Reminded ✓')
    expect(findByTestId(nudged, 'row-remind').attributes('disabled')).toBeDefined()
  })

  it('offers nothing at all when the row is clear', () => {
    const square = mount(BalanceRow, { props: { ...base, owedMinor: 0 } })
    expect(square.text()).toContain('All square')
    expect(square.findAll('button')).toHaveLength(0)
  })

  it('offers nothing while a claim is waiting on somebody', () => {
    // Tapping Pay twice because the first one is still pending is how a debt gets paid twice.
    const pending = mount(BalanceRow, { props: { ...base, owedMinor: -3910, pending: true } })
    expect(pending.text()).toContain('Waiting for confirmation')
    expect(pending.findAll('button')).toHaveLength(0)
  })
})

describe('GroupCard', () => {
  const members = [{ id: '1', displayName: 'Bob', personHue: 1 }]

  it('is all square only at exactly zero', () => {
    // Tally compared a float against 0.005. Four cents is not square, and saying so would be the
    // app quietly writing off somebody's money.
    expect(mount(GroupCard, { props: { name: 'Ski', members, yourNetMinor: 0 } }).text()).toContain(
      'All square',
    )
    const almost = mount(GroupCard, { props: { name: 'Ski', members, yourNetMinor: 4 } })
    expect(almost.text()).not.toContain('All square')
    expect(almost.text()).toContain('$0.04')
  })

  it('counts people in words that fit the number', () => {
    expect(mount(GroupCard, { props: { name: 'Ski', members, yourNetMinor: 0 } }).text()).toContain(
      '1 person',
    )
    const two = [...members, { id: '2', displayName: 'Mei', personHue: 2 }]
    expect(mount(GroupCard, { props: { name: 'Ski', members: two, yourNetMinor: 0 } }).text()).toContain(
      '2 people',
    )
  })
})

describe('ProgressBar', () => {
  it('clamps an overpayment rather than overflowing the bar', () => {
    const over = mount(ProgressBar, { props: { coveredMinor: 15_000, ofMinor: 10_000 } })
    expect(over.find('.bar__fill').attributes('style')).toContain('width: 100%')
  })

  it('shows nothing rather than dividing by zero', () => {
    const nothing = mount(ProgressBar, { props: { coveredMinor: 0, ofMinor: 0 } })
    expect(nothing.find('.bar__fill').attributes('style')).toContain('width: 0%')
  })
})

describe('TallyStepper', () => {
  it('stays inside its bounds and stays whole', () => {
    const stepper = mount(TallyStepper, { props: { modelValue: 1, min: 1, max: 3 } })
    expect(stepper.findAll('button')[0]!.attributes('disabled')).toBeDefined()

    const mid = mount(TallyStepper, { props: { modelValue: 2, min: 1, max: 3 } })
    mid.findAll('button')[1]!.trigger('click')
    expect(mid.emitted('update:modelValue')?.[0]).toEqual([3])
  })
})

describe('TallyKeypad', () => {
  it('emits digits and a delete, and offers no decimal point', () => {
    const pad = mount(TallyKeypad)
    const labels = pad.findAll('button').map((b) => b.attributes('aria-label'))

    expect(labels).toContain('Delete')
    expect(labels).toContain('00')
    // A decimal key would invite somebody to type one and expect a float; the caller shifts
    // digits into whole cents instead.
    expect(labels).not.toContain('.')
  })
})

describe('AppBar', () => {
  it('keeps the icon slug and the human label apart', () => {
    // The action string was once both at the same time, so "Invite" rendered the fallback glyph
    // and 中文 would have asked for an icon named 邀请.
    const bar = mount(AppBar, { props: { title: 'Osaka', action: 'user-plus', actionLabel: '邀请' } })
    const action = bar.find('[data-testid="appbar-action"]')
    expect(action.attributes('aria-label')).toBe('邀请')
    expect(bar.findAllComponents(TallyIcon).some((i) => i.props('name') === 'user-plus')).toBe(true)
  })
})
