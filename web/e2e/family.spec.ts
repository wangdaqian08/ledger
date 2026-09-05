import {expect, test} from '@playwright/test'
import {addMembers, createTrip, expectRowsToSumToHero, signIn, typeAmount, uniquePerson} from './helpers'

/**
 * Family mode on Settle-up (§7b): a trip member partitions the whole trip into Families — built
 * multi-person ones plus an automatic one-person Family for everyone left out — and each card
 * shows that Family's own net plus one bilateral row per *other* Family. It is entirely ephemeral
 * (nothing here is persisted) and additive: the "By person" rows behind the toggle are the
 * existing Settle-up screen, untouched.
 */

test('building a family combines nets and shows a genuine family-vs-family figure', async ({ page }) => {
  const aliceName = uniquePerson('Alice')
  await signIn(page, aliceName)
  await createTrip(page, 'Family math')
  await addMembers(page, ['Bob', 'Cathy', 'Dana', 'Erin'])

  // The spec's own worked example (mirrored in SettlementFamilyApiTest.fiveWayTrip): a $100
  // dinner shared by all five, a $20 taxi shared by just Bob and Cathy, and $20 of groceries
  // shared by just Dana and Erin — enough cross-pair debt that a Family-vs-Family figure can
  // never be read off a single person's row.
  await page.getByTestId('add-expense').click()
  await typeAmount(page, '10000')
  await page.getByTestId('expense-title').fill('Dinner')
  await page.getByTestId('next-step').click()
  await page.getByTestId('split-all').click()
  await page.getByTestId('save-expense').click()
  await expect(page.getByTestId('expense-row').filter({ hasText: 'Dinner' })).toBeVisible()

  await page.getByTestId('add-expense').click()
  await typeAmount(page, '2000')
  await page.getByTestId('expense-title').fill('Taxi')
  await page.getByTestId('next-step').click()
  await page.getByTestId('payer-chip').filter({ hasText: 'Bob' }).click()
  await page.getByTestId('person-toggle').filter({ hasText: 'Bob' }).click()
  await page.getByTestId('person-toggle').filter({ hasText: 'Cathy' }).click()
  await page.getByTestId('save-expense').click()
  await expect(page.getByTestId('expense-row').filter({ hasText: 'Taxi' })).toBeVisible()

  await page.getByTestId('add-expense').click()
  await typeAmount(page, '2000')
  await page.getByTestId('expense-title').fill('Groceries')
  await page.getByTestId('next-step').click()
  await page.getByTestId('payer-chip').filter({ hasText: 'Dana' }).click()
  await page.getByTestId('person-toggle').filter({ hasText: 'Dana' }).click()
  await page.getByTestId('person-toggle').filter({ hasText: 'Erin' }).click()
  await page.getByTestId('save-expense').click()
  await expect(page.getByTestId('expense-row').filter({ hasText: 'Groceries' })).toBeVisible()
  await expectRowsToSumToHero(page)

  // By person, before any grouping: Bob, Cathy, Dana and Erin each owe Alice exactly $20 — their
  // dinner share, the only item that ever puts them opposite her.
  await page.getByTestId('settle-up').click()
  const personRows = page.getByTestId('sheet-panel').getByTestId('balance-row')
  for (const name of ['Bob', 'Cathy', 'Dana', 'Erin']) {
    const row = personRows.filter({ hasText: name })
    await expect(row).toContainText('Owes you')
    await expect(row).toContainText('20.00')
  }
  await page.getByTestId('settle-done').click()
  await expect(page.getByTestId('trip-position')).toContainText('You are owed')
  await expect(page.getByTestId('trip-position')).toContainText('$80.00')

  // Family mode, nothing built yet: nothing to show — not five relabelled per-person rows.
  await page.getByTestId('settle-up').click()
  await page.getByTestId('mode-by-family').click()
  const cards = page.getByTestId('family-card')
  await expect(cards).toHaveCount(0)
  await expect(page.getByTestId('no-families-yet')).toBeVisible()

  // Group Alice with Bob: $80 (Alice, owed) + (-$10) (Bob: paid $20 for the taxi, owes $20 for
  // dinner plus $10 for his own taxi share) = $70 owed to the family.
  await page.getByTestId('build-family').click()
  await page.getByTestId('person-toggle').filter({ hasText: 'You' }).click()
  await page.getByTestId('person-toggle').filter({ hasText: 'Bob' }).click()
  await page.getByTestId('family-builder-add').click()
  await expect(cards).toHaveCount(4)

  // Group Cathy with Dana too, so at least one counterpart is a genuine family-vs-family row —
  // never the degenerate family-vs-one-person case a single grouping would leave every row as.
  await page.getByTestId('build-family').click()
  await page.getByTestId('person-toggle').filter({ hasText: 'Cathy' }).click()
  await page.getByTestId('person-toggle').filter({ hasText: 'Dana' }).click()
  await page.getByTestId('family-builder-add').click()
  await expect(cards).toHaveCount(3)

  const familyAB = cards.filter({ hasText: `${aliceName} and Bob` })
  await expect(familyAB).toContainText('70.00')

  // The Cathy+Dana row on Alice+Bob's card is the raw sum of four pairwise debts — Alice-Cathy
  // $20, Alice-Dana $20, Bob-Cathy $10 (the taxi), Bob-Dana $0 — which totals $50, and is never
  // reducible to either person's own row (Cathy alone owes $30 in total, Dana alone owes $10).
  const abVsCd = familyAB.getByTestId('family-counterpart-row').filter({ hasText: 'Cathy and Dana' })
  await expect(abVsCd).toContainText(`Cathy and Dana owe ${aliceName} and Bob`)
  await expect(abVsCd).toContainText('50.00')
  const abVsErin = familyAB.getByTestId('family-counterpart-row').filter({ hasText: 'Erin' })
  await expect(abVsErin).toContainText(`Erin owes ${aliceName} and Bob`)
  await expect(abVsErin).toContainText('20.00')

  // And Cathy+Dana's own card agrees exactly, the other way round.
  const familyCD = cards.filter({ hasText: 'Cathy and Dana' })
  await expect(familyCD).toContainText('40.00')
  const cdVsAb = familyCD.getByTestId('family-counterpart-row').filter({ hasText: `${aliceName} and Bob` })
  await expect(cdVsAb).toContainText(`Cathy and Dana owe ${aliceName} and Bob`)
  await expect(cdVsAb).toContainText('50.00')
})

test('undo returns a built family to two singleton cards, with their original figures', async ({ page }) => {
  const aliceName = uniquePerson('Alice')
  await signIn(page, aliceName)
  await createTrip(page, 'Family undo')
  await addMembers(page, ['Bob', 'Cathy'])

  // $30 dinner, split three ways: Alice fronts it, so she nets +$20; Bob and Cathy each net -$10.
  // Bob's only relationship in this trip is with Alice, so that pairwise figure is also his own
  // overall net — which is what lets this test check the combined family net by hand.
  await page.getByTestId('add-expense').click()
  await typeAmount(page, '3000')
  await page.getByTestId('expense-title').fill('Dinner')
  await page.getByTestId('next-step').click()
  await page.getByTestId('split-all').click()
  await page.getByTestId('save-expense').click()
  await expect(page.getByTestId('expense-row').filter({ hasText: 'Dinner' })).toBeVisible()
  await expectRowsToSumToHero(page)

  await page.getByTestId('settle-up').click()
  await expect(page.getByTestId('trip-position')).toContainText('$20.00')
  const bobPersonRow = page.getByTestId('sheet-panel').getByTestId('balance-row').filter({ hasText: 'Bob' })
  await expect(bobPersonRow).toContainText('Owes you')
  await expect(bobPersonRow).toContainText('10.00')

  await page.getByTestId('mode-by-family').click()
  const cards = page.getByTestId('family-card')
  await expect(cards).toHaveCount(0) // nothing built yet
  await expect(page.getByTestId('no-families-yet')).toBeVisible()

  await page.getByTestId('build-family').click()
  await page.getByTestId('person-toggle').filter({ hasText: 'You' }).click()
  await page.getByTestId('person-toggle').filter({ hasText: 'Bob' }).click()
  await page.getByTestId('family-builder-add').click()

  const familyCard = cards.filter({ hasText: `${aliceName} and Bob` })
  await expect(cards).toHaveCount(2)
  await expect(familyCard).toContainText('10.00') // $20 (Alice) - $10 (Bob) = $10, owed to the family
  await expect(familyCard.getByTestId('family-undo')).toBeVisible()
  await familyCard.getByTestId('family-undo').click()

  // Back to nothing built — the empty state, not three singleton cards.
  await expect(cards).toHaveCount(0)
  await expect(page.getByTestId('no-families-yet')).toBeVisible()
})

test('reopening settle-up resets family mode to by-person, with nothing built', async ({ page }) => {
  await signIn(page, uniquePerson('Alice'))
  await createTrip(page, 'Family reset')
  await addMembers(page, ['Bob', 'Cathy'])

  await page.getByTestId('settle-up').click()
  await expect(page.getByTestId('mode-by-person')).toHaveAttribute('aria-pressed', 'true')

  await page.getByTestId('mode-by-family').click()
  await page.getByTestId('build-family').click()
  await page.getByTestId('person-toggle').filter({ hasText: 'You' }).click()
  await page.getByTestId('person-toggle').filter({ hasText: 'Bob' }).click()
  await page.getByTestId('family-builder-add').click()
  await expect(page.getByTestId('family-card')).toHaveCount(2) // {Alice,Bob} + Cathy

  await page.getByTestId('settle-done').click()
  await expect(page.getByTestId('sheet-panel')).toHaveCount(0)

  await page.getByTestId('settle-up').click()
  await expect(page.getByTestId('sheet-panel')).toBeVisible()
  await expect(page.getByTestId('mode-by-person')).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByTestId('mode-by-family')).toHaveAttribute('aria-pressed', 'false')

  // Switching to family mode again: zero explicit families remain, so it's the empty state again,
  // not the {Alice,Bob} grouping built before this reopen.
  await page.getByTestId('mode-by-family').click()
  await expect(page.getByTestId('family-card')).toHaveCount(0)
  await expect(page.getByTestId('no-families-yet')).toBeVisible()
})

test('ticking every candidate on the first build is blocked before submission, with a real reason', async ({
  page,
}) => {
  await signIn(page, uniquePerson('Alice'))
  await createTrip(page, 'Family overflow')
  await addMembers(page, ['Bob'])

  await page.getByTestId('settle-up').click()
  await page.getByTestId('mode-by-family').click()
  const cards = page.getByTestId('family-card')
  await expect(cards).toHaveCount(0) // nothing built yet — the empty state, not two singletons

  // Ticking every candidate on this, the trip's first-ever build, would leave nothing to
  // auto-singleton and only one family overall — the rule FamilyBuilder itself blocks, with a
  // reason shown, rather than let it round-trip to the server's own 400 for the same thing.
  await page.getByTestId('build-family').click()
  const toggles = page.getByTestId('person-toggle')
  await expect(toggles).toHaveCount(2)
  await toggles.nth(0).click()
  await toggles.nth(1).click()

  const hint = page.getByTestId('family-builder-hint')
  await expect(hint).toContainText("can't be everyone")
  await expect(page.getByTestId('family-builder-add')).toBeDisabled()
  // Not a crash, not a silent no-op, and not a bogus "1 family" result: nothing was submitted, so
  // the builder is still the only thing open — no cards, because nothing was ever built.
  await expect(cards).toHaveCount(0)

  // Not a blanket block either: leaving one person out clears the hint, re-enables Add, and that
  // (now merely one-person) selection commits normally.
  await toggles.nth(1).click()
  await expect(hint).toHaveCount(0)
  await expect(page.getByTestId('family-builder-add')).toBeEnabled()
  await page.getByTestId('family-builder-add').click()
  await expect(cards).toHaveCount(2)
})

test('switching to family mode and back leaves the per-person rows untouched', async ({ page }) => {
  await signIn(page, uniquePerson('Alice'))
  await createTrip(page, 'Family toggle')
  await addMembers(page, ['Bob', 'Cathy'])

  await page.getByTestId('add-expense').click()
  await typeAmount(page, '3000')
  await page.getByTestId('expense-title').fill('Dinner')
  await page.getByTestId('next-step').click()
  await page.getByTestId('split-all').click()
  await page.getByTestId('save-expense').click()
  await expect(page.getByTestId('expense-row').filter({ hasText: 'Dinner' })).toBeVisible()
  await expectRowsToSumToHero(page)

  await page.getByTestId('settle-up').click()
  const personRows = page.getByTestId('sheet-panel').getByTestId('balance-row')
  await expect(personRows).toHaveCount(2)
  const before = await personRows.allInnerTexts()

  // Build a family so the toggle below genuinely re-fetches — with nothing built, switching modes
  // fetches nothing at all, which would make this guard pass for the wrong reason.
  await page.getByTestId('mode-by-family').click()
  await page.getByTestId('build-family').click()
  await page.getByTestId('person-toggle').filter({ hasText: 'You' }).click()
  await page.getByTestId('person-toggle').filter({ hasText: 'Bob' }).click()
  await page.getByTestId('family-builder-add').click()
  await expect(page.getByTestId('family-card')).toHaveCount(2)

  // Family mode is a pure read, never routed through the sheet's act() helper — switching into
  // it and back must never refetch or mutate the per-person rows underneath.
  await page.getByTestId('mode-by-person').click()
  await expect(personRows).toHaveCount(2)
  expect(await personRows.allInnerTexts()).toEqual(before)
})
