import { describe, expect, it } from 'vitest'
import { familyDisplayName, familyGrammaticalCount } from '@/lib/family'
import type { FamilyMemberView } from '@/lib/api'

const messages: Record<string, string> = {
  'settle.familyNameYouSolo': "{name}'s household",
  'settle.familyNameYouGroup': '{name} and another {count} household',
}

/** A plain-interpolation stand-in for useI18n's t — this codebase never uses ICU pluralization. */
function t(key: string, params: Record<string, unknown> = {}): string {
  return Object.entries(params).reduce(
    (text, [name, value]) => text.replaceAll(`{${name}}`, String(value)),
    messages[key] ?? key,
  )
}

function member(over: Partial<FamilyMemberView> = {}): FamilyMemberView {
  return { id: 'm-1', displayName: 'Luck', personHue: 1, isYou: false, ...over }
}

describe('familyDisplayName', () => {
  it("names the viewer's own solo family after them, possessively", () => {
    expect(familyDisplayName([member({ isYou: true, displayName: 'Luck' })], t)).toBe("Luck's household")
  })

  it("names the viewer's own multi-person family after them, plus how many more", () => {
    const members = [
      member({ isYou: true, displayName: 'Luck' }),
      member({ id: 'm-2', displayName: 'Bob' }),
      member({ id: 'm-3', displayName: 'Cara' }),
      member({ id: 'm-4', displayName: 'Dana' }),
    ]
    expect(familyDisplayName(members, t)).toBe('Luck and another 3 household')
  })

  it('finds the viewer wherever they sit in the list, not only when listed first', () => {
    const members = [member({ id: 'm-2', displayName: 'Bob' }), member({ isYou: true, displayName: 'Luck' })]
    expect(familyDisplayName(members, t)).toBe('Luck and another 1 household')
  })

  it('names another family by one arbitrary member, never every name joined', () => {
    // The very case this naming exists to avoid: "Peter, Jack and Luck owe Rose money" is too long
    // to parse as a sentence subject at a glance.
    const members = [
      member({ id: 'm-2', displayName: 'Peter' }),
      member({ id: 'm-3', displayName: 'Jack' }),
      member({ id: 'm-4', displayName: 'Luck' }),
    ]
    expect(familyDisplayName(members, t)).toBe('Peter')
    expect(familyDisplayName(members, t)).not.toContain('Jack')
  })

  it('names a solo other family by the one person in it, with no household framing', () => {
    expect(familyDisplayName([member({ displayName: 'Rose' })], t)).toBe('Rose')
  })
})

describe('familyGrammaticalCount', () => {
  it("counts for real when the viewer's own family is the subject", () => {
    const members = [member({ isYou: true }), member({ id: 'm-2' }), member({ id: 'm-3' })]
    expect(familyGrammaticalCount(members)).toBe(3)
  })

  it('reads as one when the name shown is just a single bare person, whatever the real size', () => {
    const members = [member({ id: 'm-2' }), member({ id: 'm-3' }), member({ id: 'm-4' })]
    expect(familyGrammaticalCount(members)).toBe(1)
  })
})
