import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import AppBar from '../src/components/AppBar.vue'
import BalanceRow from '../src/components/BalanceRow.vue'
import ExpenseRow from '../src/components/ExpenseRow.vue'
import FamilyBalanceCard from '../src/components/FamilyBalanceCard.vue'
import FamilyCounterpartRow from '../src/components/FamilyCounterpartRow.vue'
import GroupCard from '../src/components/GroupCard.vue'
import ProgressBar from '../src/components/ProgressBar.vue'
import TallyIcon from '../src/components/TallyIcon.vue'
import TallyStepper from '../src/components/TallyStepper.vue'
import TallyKeypad from '../src/components/TallyKeypad.vue'
import type {FamilyCounterpartView, FamilyMemberView} from '@/lib/api'
import {findAllByTestId, findByTestId} from './testids'

describe('ExpenseRow', () => {
  const base = { title: 'Hotel', yourShareMinor: -14286, categoryKey: 'stay' }

  it('shows your stake in the bill in whole cents, framed as a share not a debt', () => {
    // Somebody else paid, so this is your fixed share of the bill — never worded "you owe", which
    // made a person who had already paid it back read it as money still outstanding. Magnitude only.
    const row = mount(ExpenseRow, { props: base })
    expect(row.text()).toContain('$142.86')
    expect(row.text()).toContain('your share')
    expect(row.text()).not.toContain('you owe')
  })

  it('calls the payer\'s stake what they fronted, not what they "get"', () => {
    const row = mount(ExpenseRow, { props: { ...base, yourShareMinor: 37_500, paidByYou: true } })
    expect(row.text()).toContain('$375.00')
    expect(row.text()).toContain('you fronted')
    expect(row.text()).not.toContain('you get')
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

    // Tapping Remind sends nothing (push is out of phase 1), so the button turns into an honest
    // instruction — never a "delivered ✓" that would claim a reminder the app never sent.
    const nudged = mount(BalanceRow, { props: { ...base, owedMinor: 4230, reminded: true } })
    expect(nudged.text()).toContain('Nudge them yourself')
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

describe('FamilyCounterpartRow', () => {
  const members: FamilyMemberView[] = [
    { id: 'm-c', displayName: 'Cara', personHue: 3 },
    { id: 'm-d', displayName: 'Dana', personHue: 4 },
  ]

  it('names both sides when the counterpart owes, with no Pay or Remind button — nobody could tap it on its behalf', () => {
    const row = mount(FamilyCounterpartRow, {
      props: { members, owedMinor: 500, cardName: 'Alice and Bob', cardMemberCount: 2 },
    })
    expect(row.text()).toContain('Cara and Dana')
    expect(row.text()).toContain('owe')
    expect(row.text()).toContain('Alice and Bob')
    expect(row.text()).toContain('$5.00')
    expect(row.findAll('button')).toHaveLength(0)
  })

  it('names both sides the other way round when this card owes the counterpart', () => {
    const row = mount(FamilyCounterpartRow, {
      props: { members, owedMinor: -1250, cardName: 'Alice and Bob', cardMemberCount: 2 },
    })
    expect(row.text()).toContain('Alice and Bob')
    expect(row.text()).toContain('owe')
    expect(row.text()).toContain('Cara and Dana')
    expect(row.text()).toContain('$12.50')
  })

  it('conjugates for a one-person subject ("owes"), picked by the subject\'s own count', () => {
    // The card (Erin, one person) owes the counterpart here, so the card is the subject and it is
    // exactly one person — "Erin owes", never "Erin owe" — regardless of the counterpart's own size.
    const row = mount(FamilyCounterpartRow, {
      props: { members, owedMinor: -1250, cardName: 'Erin', cardMemberCount: 1 },
    })
    expect(row.text()).toContain('Erin owes')
  })

  it('conjugates for a multi-person subject ("owe")', () => {
    // Now the counterpart (Cara and Dana, two people) owes the card — the counterpart is the
    // subject, and it is two people, so "owe", never "owes".
    const row = mount(FamilyCounterpartRow, {
      props: { members, owedMinor: 500, cardName: 'Erin', cardMemberCount: 1 },
    })
    expect(row.text()).toContain('Cara and Dana owe')
  })

  it('bolds the verb itself, never the names either side of it', () => {
    const row = mount(FamilyCounterpartRow, {
      props: { members, owedMinor: 500, cardName: 'Alice and Bob', cardMemberCount: 2 },
    })
    const strong = row.find('strong')
    expect(strong.exists()).toBe(true)
    expect(strong.text()).toBe('owe')
    // The names are still there, just not inside the bolded element — plain text either side of it.
    expect(row.find('.counterpart__sentence').text()).toBe('Cara and Dana owe Alice and Bob')
  })

  it('reads all square at exactly zero, not a tolerance', () => {
    const row = mount(FamilyCounterpartRow, {
      props: { members, owedMinor: 0, cardName: 'Alice and Bob', cardMemberCount: 2 },
    })
    expect(row.text()).toContain('All square')
  })

  it('joins every member of the counterpart into one label', () => {
    const row = mount(FamilyCounterpartRow, {
      props: { members, owedMinor: 0, cardName: 'Alice and Bob', cardMemberCount: 2 },
    })
    expect(row.text()).toContain('Cara')
    expect(row.text()).toContain('Dana')
  })
})

describe('FamilyBalanceCard', () => {
  const members: FamilyMemberView[] = [
    { id: 'm-a', displayName: 'Alice', personHue: 1 },
    { id: 'm-b', displayName: 'Bob', personHue: 2 },
  ]
  const counterparts: FamilyCounterpartView[] = [
    { members: [{ id: 'm-c', displayName: 'Cara', personHue: 3 }], owedMinor: 500 },
    { members: [{ id: 'm-d', displayName: 'Dana', personHue: 4 }], owedMinor: -300 },
  ]

  it("shows the family's own net and one row per counterpart, never per individual", () => {
    const card = mount(FamilyBalanceCard, {
      props: { members, netMinor: 200, counterparts, removable: false },
    })
    expect(card.text()).toContain('$2.00')
    expect(card.findAllComponents(FamilyCounterpartRow)).toHaveLength(2)
  })

  it('flips the wire sign once for each counterpart row, the same way SettleUpSheet flips it for BalanceRow', () => {
    // The API states owedMinor from the enclosing Family's own point of view (positive = this
    // Family owes them, matching SettlementRow.owedMinor's convention) — the same figure
    // FamilyCounterpartRow displays the other way round (positive = they owe this Family), so the
    // call site negates it exactly once, same as SettleUpSheet.vue does for BalanceRow.
    const card = mount(FamilyBalanceCard, {
      props: { members, netMinor: 0, counterparts, removable: false },
    })
    const rows = card.findAllComponents(FamilyCounterpartRow)
    expect(rows[0]!.props('owedMinor')).toBe(-500) // raw +500 (this family owes Cara) -> Cara's row is -500
    expect(rows[1]!.props('owedMinor')).toBe(300) // raw -300 (Dana owes this family) -> Dana's row is +300
  })

  it('offers Undo only when explicitly built, never for an auto-singleton', async () => {
    const singleton = mount(FamilyBalanceCard, {
      props: { members, netMinor: 0, counterparts: [], removable: false },
    })
    expect(findAllByTestId(singleton, 'family-undo')).toHaveLength(0)

    const built = mount(FamilyBalanceCard, {
      props: { members, netMinor: 0, counterparts: [], removable: true },
    })
    expect(findAllByTestId(built, 'family-undo')).toHaveLength(1)

    await findByTestId(built, 'family-undo').trigger('click')
    expect(built.emitted('remove')).toHaveLength(1)
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
