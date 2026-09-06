import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AmountInput from '../src/components/AmountInput.vue'
import AmountKeypadField from '../src/components/AmountKeypadField.vue'
import CategoryPicker from '../src/components/CategoryPicker.vue'
import CommentField from '../src/components/CommentField.vue'
import PersonToggleRow from '../src/components/PersonToggleRow.vue'
import SplitBar from '../src/components/SplitBar.vue'
import { saltFor, splitShares } from '@/lib/split'
import { pressKey } from '@/lib/till'
import { COMMENT_MAX_CHARS, COMMENT_MAX_WORDS } from '@/lib/words'
import { findAllByTestId, findByTestId } from './testids'
import FamilyBuilder from '@/components/FamilyBuilder.vue'
import type { MemberView } from '@/lib/api'

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

describe('CommentField', () => {
  const words = (count: number) => Array.from({ length: count }, (_, i) => `w${i}`).join(' ')

  it('stays folded away until it is asked for', async () => {
    const field = mount(CommentField, { props: { modelValue: '' } })
    expect(findByTestId(field, 'comment-input').exists()).toBe(false)

    await findByTestId(field, 'comment-toggle').trigger('click')

    expect(findByTestId(field, 'comment-input').exists()).toBe(true)
    expect(findByTestId(field, 'comment-toggle').attributes('aria-expanded')).toBe('true')
  })

  it('opens already unfolded when there is a comment to show', () => {
    const field = mount(CommentField, { props: { modelValue: 'Split the taxi', open: true } })
    const box = findByTestId(field, 'comment-input').element as HTMLTextAreaElement
    expect(box.value).toBe('Split the taxi')
    // The hard backstop is the native attribute, silent and unmentioned: it is there for scripts
    // that write no spaces, where the word count alone would bound nothing.
    expect(box.getAttribute('maxlength')).toBe(String(COMMENT_MAX_CHARS))
  })

  it('hands the fold back to its parent instead of keeping a second opinion', async () => {
    // The detail sheet draws Save and Discard beside this field and has to know whether the box
    // is up. A private `open` lets the two disagree — buttons over a box that is not there.
    const field = mount(CommentField, { props: { modelValue: '', open: false } })

    await findByTestId(field, 'comment-toggle').trigger('click')
    expect(field.emitted('update:open')?.at(-1)).toEqual([true])

    await field.setProps({ open: true })
    expect(findByTestId(field, 'comment-input').exists()).toBe(true)

    // And the prop is obeyed after mount, not only read once on the way in.
    await field.setProps({ open: false })
    expect(findByTestId(field, 'comment-input').exists()).toBe(false)
  })

  it('counts the words as they are typed', async () => {
    const field = mount(CommentField, { props: { modelValue: '', open: true } })
    expect(findByTestId(field, 'comment-count').text()).toContain(`0/${COMMENT_MAX_WORDS}`)

    await findByTestId(field, 'comment-input').setValue('  dinner   at the   rose  ')

    expect(field.emitted('update:modelValue')?.at(-1)).toEqual(['  dinner   at the   rose  '])
    await field.setProps({ modelValue: '  dinner   at the   rose  ' })
    expect(findByTestId(field, 'comment-count').text()).toContain(`4/${COMMENT_MAX_WORDS}`)
  })

  it('goes red and tells its parent when the comment runs past the limit, without eating the text', async () => {
    const field = mount(CommentField, { props: { modelValue: words(COMMENT_MAX_WORDS), open: true } })
    expect(findByTestId(field, 'comment-count').classes()).not.toContain('comment__count--over')
    // Exactly at the limit is legal, and there is nothing to tell the parent about.
    expect(field.emitted('update:invalid')).toBeUndefined()

    const tooMany = words(COMMENT_MAX_WORDS + 1)
    await field.setProps({ modelValue: tooMany })

    expect(findByTestId(field, 'comment-count').classes()).toContain('comment__count--over')
    expect(field.emitted('update:invalid')?.at(-1)).toEqual([true])
    // Nothing is truncated mid-sentence: the text stays as typed and the counter does the telling.
    expect((findByTestId(field, 'comment-input').element as HTMLTextAreaElement).value).toBe(tooMany)

    // And the parent is told when the way is clear again, or its Save would stay dead.
    await field.setProps({ modelValue: 'back under' })
    expect(field.emitted('update:invalid')?.at(-1)).toEqual([false])
  })

  it('keeps the reason on screen when the limit is broken and the box is folded away', async () => {
    // The Save above is disabled off the back of this. Folding the box away took the only
    // explanation with it, leaving a dead button at the bottom of a long scroll and no reason why.
    const field = mount(CommentField, {
      props: { modelValue: words(COMMENT_MAX_WORDS + 1), open: false },
    })

    expect(findByTestId(field, 'comment-input').exists()).toBe(false)
    expect(findByTestId(field, 'comment-count').classes()).toContain('comment__count--over')
    expect(findByTestId(field, 'comment-count').text()).toContain(`101/${COMMENT_MAX_WORDS}`)

    // Back under the limit and the count goes back to being the open box's business alone.
    await field.setProps({ modelValue: 'short enough' })
    expect(findByTestId(field, 'comment-count').exists()).toBe(false)
  })

  it('gives the box a name of its own, and the count as its description', async () => {
    // A placeholder is the accessible-name algorithm's last resort and disappears the moment
    // somebody types. The label lives inside a button, so no <label for> can reach the box.
    const field = mount(CommentField, { props: { modelValue: words(COMMENT_MAX_WORDS + 1), open: true } })
    const box = findByTestId(field, 'comment-input')
    const toggle = findByTestId(field, 'comment-toggle')
    const count = findByTestId(field, 'comment-count')

    expect(field.get(`#${box.attributes('aria-labelledby')}`).text()).toBe('Add a comment (optional)')
    // aria-invalid says something is wrong; the description is what says what.
    expect(box.attributes('aria-invalid')).toBe('true')
    expect(box.attributes('aria-describedby')).toBe(count.attributes('id'))
    expect(field.find(`#${toggle.attributes('aria-controls')}`).exists()).toBe(true)
  })

  it('does not re-announce the count on every keystroke', () => {
    // A live region here queues "1/100 words", "2/100 words"… against the screen reader's own
    // character echo. The count reaches the box through aria-describedby instead.
    const field = mount(CommentField, { props: { modelValue: '', open: true } })

    expect(findByTestId(field, 'comment-count').attributes('aria-live')).toBe('off')
  })

  it('leaves an in-flight composition alone until the IME commits it', async () => {
    // Pinyin arrives as a buffer that is rewritten several times before it becomes characters.
    // Vue's own v-model installs the composition guard; a hand-rolled :value/@input pair does not,
    // and the half-typed buffer flows straight into the word count and the parent's dirty flag.
    const field = mount(CommentField, { props: { modelValue: '', open: true } })
    const box = findByTestId(field, 'comment-input')
    const element = box.element as HTMLTextAreaElement

    await box.trigger('compositionstart')
    element.value = 'ni'
    await box.trigger('input')
    expect(field.emitted('update:modelValue')).toBeUndefined()

    element.value = '你好'
    await box.trigger('compositionend')
    expect(field.emitted('update:modelValue')?.at(-1)).toEqual(['你好'])
  })

  it('counts characters instead when they, not the words, are the wall being approached', async () => {
    // Chinese writes no spaces, so a comment of any length is one word and the word counter never
    // moves — while the silent 1200-character backstop is what actually stops the typing.
    const chinese = '晚饭是我付的'.repeat(20)
    const field = mount(CommentField, { props: { modelValue: chinese, open: true } })

    expect(findByTestId(field, 'comment-count').text()).toContain(`${chinese.length}/${COMMENT_MAX_CHARS}`)

    // English prose hits the word limit first, so that is still what it is measured against.
    await field.setProps({ modelValue: words(20) })
    expect(findByTestId(field, 'comment-count').text()).toContain(`20/${COMMENT_MAX_WORDS}`)
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
describe('FamilyBuilder', () => {
  // Only the *unassigned* candidates ever reach this component — SettleUpSheet excludes anyone
  // already placed in a built Family structurally, before this component ever mounts.
  const candidates: MemberView[] = [
    { id: 'm-c', displayName: 'Cara', personHue: 3, claimed: true, isYou: false },
    { id: 'm-d', displayName: 'Dana', personHue: 4, claimed: true, isYou: false },
  ]

  it('commits only the ticked ids, in candidate order', async () => {
    const builder = mount(FamilyBuilder, { props: { candidates, mustLeaveOneOut: false } })

    await findAllByTestId(builder, 'person-toggle')[1]!.trigger('click') // Dana
    await findAllByTestId(builder, 'person-toggle')[0]!.trigger('click') // Cara

    await findByTestId(builder, 'family-builder-add').trigger('click')

    expect(builder.emitted('built')).toEqual([[['m-c', 'm-d']]])
  })

  it('disables the commit until somebody is ticked', async () => {
    const builder = mount(FamilyBuilder, { props: { candidates, mustLeaveOneOut: false } })
    expect(findByTestId(builder, 'family-builder-add').attributes('disabled')).toBeDefined()

    await findAllByTestId(builder, 'person-toggle')[0]!.trigger('click')
    expect(findByTestId(builder, 'family-builder-add').attributes('disabled')).toBeUndefined()

    // Unticking back to nobody disables it again — not a one-way latch.
    await findAllByTestId(builder, 'person-toggle')[0]!.trigger('click')
    expect(findByTestId(builder, 'family-builder-add').attributes('disabled')).toBeDefined()
  })

  it('cancel emits nothing committed', async () => {
    const builder = mount(FamilyBuilder, { props: { candidates, mustLeaveOneOut: false } })
    await findAllByTestId(builder, 'person-toggle')[0]!.trigger('click')
    await findByTestId(builder, 'family-builder-cancel').trigger('click')

    expect(builder.emitted('cancel')).toHaveLength(1)
    expect(builder.emitted('built')).toBeUndefined()
  })

  // §7b: a single Family naming everyone leaves nobody to auto-singleton — the server refuses it,
  // so the very first Family built this session is blocked from ticking every candidate at all,
  // rather than letting the viewer hit that refusal after already committing.
  it('blocks ticking everyone only when this would be the trip-covering first family', async () => {
    const builder = mount(FamilyBuilder, { props: { candidates, mustLeaveOneOut: true } })

    await findAllByTestId(builder, 'person-toggle')[0]!.trigger('click')
    await findAllByTestId(builder, 'person-toggle')[1]!.trigger('click')
    expect(findByTestId(builder, 'family-builder-add').attributes('disabled')).toBeDefined()
    expect(builder.find('[data-testid="family-builder-hint"]').exists()).toBe(true)

    // Leaving even one person out is fine again.
    await findAllByTestId(builder, 'person-toggle')[1]!.trigger('click')
    expect(findByTestId(builder, 'family-builder-add').attributes('disabled')).toBeUndefined()
    expect(builder.find('[data-testid="family-builder-hint"]').exists()).toBe(false)
  })

  it('does not block ticking everyone once a family already exists this session', async () => {
    const builder = mount(FamilyBuilder, { props: { candidates, mustLeaveOneOut: false } })

    await findAllByTestId(builder, 'person-toggle')[0]!.trigger('click')
    await findAllByTestId(builder, 'person-toggle')[1]!.trigger('click')

    expect(findByTestId(builder, 'family-builder-add').attributes('disabled')).toBeUndefined()
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
