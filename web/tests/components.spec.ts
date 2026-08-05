import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AmountText from '../src/components/AmountText.vue'
import AvatarStack from '../src/components/AvatarStack.vue'
import PersonAvatar from '../src/components/PersonAvatar.vue'
import TallyButton from '../src/components/TallyButton.vue'

describe('AmountText', () => {
  it('renders minor units, never a float', () => {
    expect(mount(AmountText, { props: { amountMinor: 14286 } }).text()).toBe('$142.86')
  })

  it('carries the sign by tone as well as by glyph', () => {
    const owed = mount(AmountText, { props: { amountMinor: 3406, tone: 'owed', showSign: true } })
    expect(owed.text()).toBe('+$34.06')
    expect(owed.classes()).toContain('amount--owed')
  })
})

describe('PersonAvatar', () => {
  it('takes two initials from a full name and one when compact', () => {
    expect(mount(PersonAvatar, { props: { name: 'Bob Chen' } }).text()).toBe('BC')
    expect(mount(PersonAvatar, { props: { name: 'Bob Chen', compact: true } }).text()).toBe('B')
  })

  it('copes with a single name, extra spaces, and nothing at all', () => {
    expect(mount(PersonAvatar, { props: { name: 'Jack' } }).text()).toBe('J')
    expect(mount(PersonAvatar, { props: { name: '  mei   lin  ' } }).text()).toBe('ML')
    expect(mount(PersonAvatar, { props: { name: '   ' } }).text()).toBe('?')
  })

  it('wraps hues past eight rather than clamping them', () => {
    // A ninth member should look like the first, not pile onto hue 8 with everybody after them.
    const ninth = mount(PersonAvatar, { props: { name: 'Ninth', hue: 9 } })
    expect(ninth.attributes('style')).toContain('var(--person-1)')

    const eighth = mount(PersonAvatar, { props: { name: 'Eighth', hue: 8 } })
    expect(eighth.attributes('style')).toContain('var(--person-8)')
  })

  it('puts dark text on the lemon hue, which white would be unreadable on', () => {
    expect(mount(PersonAvatar, { props: { name: 'Mei', hue: 3 } }).attributes('style')).toContain(
      'var(--ink)',
    )
  })
})

describe('AvatarStack', () => {
  const people = Array.from({ length: 6 }, (_, i) => ({
    id: String(i),
    displayName: `Person ${i}`,
    personHue: i + 1,
  }))

  it('shows a few and counts the rest', () => {
    const stack = mount(AvatarStack, { props: { people, max: 4 } })
    expect(stack.findAllComponents(PersonAvatar)).toHaveLength(4)
    expect(stack.text()).toContain('+2')
  })

  it('says nothing about overflow when everybody fits', () => {
    const stack = mount(AvatarStack, { props: { people: people.slice(0, 3), max: 4 } })
    expect(stack.text()).not.toContain('+')
  })
})

describe('TallyButton', () => {
  it('emits when clicked, and stays silent when disabled', async () => {
    const button = mount(TallyButton, { slots: { default: 'Pay' } })
    await button.trigger('click')
    expect(button.emitted('click')).toHaveLength(1)

    const disabled = mount(TallyButton, { props: { disabled: true }, slots: { default: 'Pay' } })
    await disabled.trigger('click')
    expect(disabled.emitted('click')).toBeUndefined()
  })
})
