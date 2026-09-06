import type { FamilyMemberView } from '@/lib/api'

type Translate = (key: string, params?: Record<string, unknown>) => string

/**
 * A Family's label wherever one is needed — a card's own title, or its mention on another card's
 * counterpart row (§7b). Anchored on the viewer's own name when they're a member, so their own
 * Family always reads as unmistakably theirs ("Luck's household", or "Luck and another 3
 * household"); otherwise just one arbitrary member's name, since spelling out every name in a
 * multi-person Family is what produced sentences like "Peter, Jack and Luck owe Rose money" — too
 * long to parse as a subject at a glance. [familyGrammaticalCount] is the count that agrees with
 * whichever of the two this returns.
 */
export function familyDisplayName(members: FamilyMemberView[], t: Translate): string {
  const you = members.find((m) => m.isYou)
  if (!you) return members[0]!.displayName
  return members.length === 1
    ? t('settle.familyNameYouSolo', { name: you.displayName })
    : t('settle.familyNameYouGroup', { name: you.displayName, count: members.length - 1 })
}

/**
 * How many people a Family's name reads as grammatically — never the real member count for a
 * Family named after just one of its members. [familyDisplayName] hides a non-viewer Family's true
 * size behind a single bare name, so the sentence it sits in must agree with what the reader can
 * actually see ("Peter owes", not "Peter owe") rather than with how many people stand behind it.
 */
export function familyGrammaticalCount(members: FamilyMemberView[]): number {
  return members.some((m) => m.isYou) ? members.length : 1
}
