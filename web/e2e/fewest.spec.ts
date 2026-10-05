import { expect, test, type Browser, type Page } from '@playwright/test'
import { addMembers, createTrip, expectRowsToSumToHero, signIn, typeAmount, uniquePerson } from './helpers'

/**
 * "By minimum transfer" and PayID, end to end with real people (spec §2 S8, §7a). The plan is the
 * server's: the screen shows it numbered, with where to send each payment, and only the person
 * paying gets a Pay button. Paying settles nets rather than pairs, so "square" is a net of 0 —
 * the rows that are left cancel out, stay as history, and offer nothing to act on.
 */

const ORIGIN = 'http://localhost:5173'

async function phone(browser: Browser, clipboard = false): Promise<Page> {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
  if (clipboard) {
    await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: ORIGIN })
  }
  return context.newPage()
}

/** One expense through the sheet: who paid (a payer chip, null for "You") and who shared it. */
async function addExpense(
  page: Page,
  title: string,
  digits: string,
  paidBy: string | null,
  sharers: string[],
) {
  await page.getByTestId('add-expense').click()
  await typeAmount(page, digits)
  await page.getByTestId('expense-title').fill(title)
  await page.getByTestId('next-step').click()
  if (paidBy) await page.getByTestId('payer-chip').filter({ hasText: paidBy }).click()
  for (const name of sharers) {
    await page.getByTestId('person-toggle').filter({ hasText: name }).click()
  }
  await page.getByTestId('save-expense').click()
  await expect(page.getByTestId('expense-row').filter({ hasText: title })).toBeVisible()
}

/** Signs a fresh person in through a share link and claims the named seat. */
async function joinAs(page: Page, link: string, seat: string) {
  await page.goto(link)
  await expect(page).toHaveURL(/\/signin/)
  await page.getByTestId('signin-name').fill(uniquePerson(seat))
  await page.getByTestId('signin-submit').click()
  await page.getByTestId('join-member').filter({ hasText: seat }).click()
  await page.getByTestId('join-claim').click()
  await expect(page).toHaveURL(/\/trips\//)
}

/** Sets the signed-in person's own PayID from People & settings, leaving the sheet open. */
async function setOwnPayId(page: Page, payId: string) {
  await page.getByTestId('appbar-action').click()
  await page.getByTestId('payid-add').click()
  await page.getByTestId('payid-input').fill(payId)
  await page.getByTestId('payid-save').click()
  const own = page.getByTestId('invite-member').filter({ has: page.getByTestId('payid-edit') })
  await expect(own.getByTestId('payid-value')).toHaveText(payId)
}

test('UC-1 weekend away: three numbered transfers, each with the PayID to send it to', async ({
  browser,
}) => {
  const ann = await phone(browser, true)
  await signIn(ann, uniquePerson('Ann'))
  const tripUrl = await createTrip(ann, 'Weekend away')
  await addMembers(ann, ['Ben', 'Cat', 'Dan', 'Eve'])

  // Every split is even with no leftover cent, so nothing here depends on the split salt.
  await addExpense(ann, 'Breakfast', '6000', null, ['You', 'Ben', 'Dan'])
  await addExpense(ann, 'Taxi', '6000', 'Ben', ['Ben', 'Cat', 'Dan', 'Eve'])
  await addExpense(ann, 'Lunch', '6000', 'Cat', ['You', 'Cat', 'Dan', 'Eve'])

  // The feed shows each bill's total, whoever paid — not Ann's stake (she had no share of the taxi).
  const taxi = ann.getByTestId('expense-row').filter({ hasText: 'Taxi' })
  await expect(taxi).toContainText('$60.00')
  await expect(taxi).toContainText(/total/i)

  // Nets: Ann +25, Ben +25, Cat +30, Dan −50, Eve −30.
  await expect(ann.getByTestId('trip-position')).toContainText('You are owed')
  await expect(ann.getByTestId('trip-position')).toContainText('$25.00')
  await expectRowsToSumToHero(ann)

  await setOwnPayId(ann, 'ann@example.com')
  await ann.getByTestId('copy-link').click()
  await expect(ann.getByTestId('invite-note')).toBeVisible()
  const link = await ann.evaluate(() => navigator.clipboard.readText())
  await ann.getByTestId('sheet-close').click()

  // Cat claims her seat in a second browser and sets her own PayID. Ann's is already visible to
  // her — read-only, with a Copy, and flagged as freshly changed.
  const cat = await phone(browser)
  await joinAs(cat, link, 'Cat')
  await setOwnPayId(cat, 'cat@example.com')
  const annRow = cat.getByTestId('invite-member').filter({ has: cat.getByTestId('payid-copy') })
  await expect(annRow.getByTestId('payid-value')).toHaveText('ann@example.com')
  await expect(annRow.getByTestId('payid-recent')).toBeVisible()
  await cat.getByTestId('sheet-close').click()

  // From Cat's chair the plan names Ann; Cat sends nothing, so nothing here offers her Pay.
  await cat.getByTestId('settle-up').click()
  await cat.getByTestId('mode-min-transfer').click()
  const catPlan = cat.getByTestId('transfer-row')
  await expect(catPlan).toHaveCount(3)
  await expect(catPlan.nth(0)).toContainText('Eve pays you')
  await expect(catPlan.nth(1)).toContainText('Dan pays Ann')
  await expect(catPlan.nth(1).getByTestId('payid-value')).toHaveText('ann@example.com')
  await expect(cat.getByTestId('transfer-pay')).toHaveCount(0)

  // Ann reloads and opens the plan: three transfers, in the server's order (the exact pair first).
  await ann.goto(tripUrl)
  await ann.getByTestId('settle-up').click()
  await ann.getByTestId('mode-min-transfer').click()
  await expect(ann.getByTestId('transfer-count')).toHaveText('3 transfers settle everyone')
  const plan = ann.getByTestId('transfer-row')
  await expect(plan).toHaveCount(3)

  await expect(plan.nth(0)).toContainText('Eve pays Cat')
  await expect(plan.nth(0)).toContainText('$30.00')
  await expect(plan.nth(0).getByTestId('payid-value')).toHaveText('cat@example.com')
  await expect(plan.nth(0).getByTestId('payid-recent')).toBeVisible()

  await expect(plan.nth(1)).toContainText('Dan pays you')
  await expect(plan.nth(1)).toContainText('$25.00')
  await expect(plan.nth(1).getByTestId('payid-value')).toHaveText('ann@example.com')

  await expect(plan.nth(2)).toContainText('Dan pays Ben')
  await expect(plan.nth(2)).toContainText('$25.00')
  await expect(plan.nth(2).getByTestId('payid-none')).toHaveText('(no PayID provided)')

  // Ann sends none of these, so no Pay anywhere in her plan.
  await expect(ann.getByTestId('transfer-pay')).toHaveCount(0)

  // Copy puts exactly the PayID on the clipboard — Cat's, then Ann's own.
  await plan.nth(0).getByTestId('payid-copy').click()
  await expect(plan.nth(0).getByTestId('payid-copy')).toContainText('Copied')
  expect(await ann.evaluate(() => navigator.clipboard.readText())).toBe('cat@example.com')
  await plan.nth(1).getByTestId('payid-copy').click()
  await expect(plan.nth(1).getByTestId('payid-copy')).toContainText('Copied')
  expect(await ann.evaluate(() => navigator.clipboard.readText())).toBe('ann@example.com')
})

test('UC-2 with Ann and Ben built into one family: the plan pays the family once, at its PayID', async ({
  browser,
}) => {
  const ann = await phone(browser)
  await signIn(ann, uniquePerson('Ann'))
  await createTrip(ann, 'Weekend families')
  await addMembers(ann, ['Ben', 'Cat', 'Dan', 'Eve'])

  // UC-1's trip again — nets Ann +25, Ben +25, Cat +30, Dan −50, Eve −30 — every split even.
  await addExpense(ann, 'Breakfast', '6000', null, ['You', 'Ben', 'Dan'])
  await addExpense(ann, 'Taxi', '6000', 'Ben', ['Ben', 'Cat', 'Dan', 'Eve'])
  await addExpense(ann, 'Lunch', '6000', 'Cat', ['You', 'Cat', 'Dan', 'Eve'])
  await expect(ann.getByTestId('trip-position')).toContainText('$25.00')
  await expectRowsToSumToHero(ann)
  await setOwnPayId(ann, 'ann@example.com')
  await ann.getByTestId('sheet-close').click()

  await ann.getByTestId('settle-up').click()
  await ann.getByTestId('mode-min-transfer').click()
  await expect(ann.getByTestId('transfer-count')).toHaveText('3 transfers settle everyone')
  await ann.getByTestId('build-family').click()
  await ann.getByTestId('person-toggle').filter({ hasText: 'You' }).click()
  await ann.getByTestId('person-toggle').filter({ hasText: 'Ben' }).click()
  await ann.getByTestId('family-builder-add').click()

  // As one party Ann and Ben are owed 50, which Dan owes exactly: two payments instead of three,
  // and the family's goes to the member with a PayID. Order is the server's, so lines are found by
  // what they say rather than where they sit.
  await expect(ann.getByTestId('transfer-families')).toHaveText('Using your families: You & Ben')
  await expect(ann.getByTestId('transfer-count')).toHaveText('2 transfers settle everyone')
  const plan = ann.getByTestId('transfer-row')
  await expect(plan).toHaveCount(2)
  const toFamily = plan.filter({ hasText: 'Dan pays you & Ben' })
  await expect(toFamily).toContainText('$50.00')
  await expect(toFamily.getByTestId('payid-value')).toHaveText('ann@example.com')
  await expect(plan.filter({ hasText: 'Eve pays Cat' })).toContainText('$30.00')
  // Ann's family is paid, not paying, so nothing here is hers to Pay.
  await expect(ann.getByTestId('transfer-pay')).toHaveCount(0)

  // One partition behind both views: By family shows the family built on the plan.
  await ann.getByTestId('mode-by-family').click()
  await expect(ann.getByTestId('family-card')).toHaveCount(4) // {Ann, Ben}, Cat, Dan, Eve

  // And Undo on the plan takes it back to the per-person plan.
  await ann.getByTestId('mode-min-transfer').click()
  await ann.getByTestId('transfer-families-undo').click()
  await expect(ann.getByTestId('transfer-families')).toHaveCount(0)
  await expect(ann.getByTestId('transfer-count')).toHaveText('3 transfers settle everyone')
})

test('paying the one suggested transfer squares everyone, and the rows that cancel stay as history', async ({
  browser,
}) => {
  const alice = await phone(browser, true)
  await signIn(alice, uniquePerson('Alice'))
  const tripUrl = await createTrip(alice, 'Chain')
  await addMembers(alice, ['Bob', 'Cy'])

  await alice.getByTestId('appbar-action').click()
  await alice.getByTestId('copy-link').click()
  await expect(alice.getByTestId('invite-note')).toBeVisible()
  const link = await alice.evaluate(() => navigator.clipboard.readText())
  await alice.getByTestId('sheet-close').click()

  const cy = await phone(browser)
  await joinAs(cy, link, 'Cy')

  // A chain: Bob covers Alice's $20 hotel, Cy covers Bob's $20 dinner. Alice owes Bob, Bob owes Cy,
  // Bob's own net is 0 — so the plan is one payment, Alice straight to Cy, skipping Bob entirely.
  await addExpense(alice, 'Hotel', '2000', 'Bob', ['You'])
  await addExpense(alice, 'Dinner', '2000', 'Cy', ['Bob'])
  await expect(alice.getByTestId('trip-position')).toContainText('You owe')
  await expect(alice.getByTestId('trip-position')).toContainText('$20.00')
  await expectRowsToSumToHero(alice)

  await alice.getByTestId('settle-up').click()
  await alice.getByTestId('mode-min-transfer').click()
  await expect(alice.getByTestId('transfer-count')).toHaveText('1 transfer settles everyone')
  const plan = alice.getByTestId('transfer-row')
  await expect(plan).toHaveCount(1)
  await expect(plan.first()).toContainText('You pay Cy')
  await expect(plan.first()).toContainText('$20.00')

  await plan.first().getByTestId('transfer-pay').click()
  await expect(alice.getByTestId('pay-amount')).toContainText('20.00')
  await alice.getByTestId('pay-send').click()

  // Filed, not settled: the claim waits on Cy, Pay is gone, and no number has moved yet.
  await expect(plan.first().getByTestId('transfer-pending')).toContainText('Sent to Cy for confirmation')
  await expect(alice.getByTestId('transfer-pay')).toHaveCount(0)
  await alice.getByTestId('settle-done').click()
  await expect(alice.getByTestId('trip-position')).toContainText('$20.00')

  // Cy, the person paid, confirms it on the By person strip — the one place a settlement is decided.
  await cy.goto(tripUrl)
  await cy.getByTestId('settle-up').click()
  const strip = cy.getByTestId('pending-claim')
  await expect(strip).toContainText('says they paid you')
  await expect(strip).toContainText('20.00')
  await strip.getByTestId('pending-approve').click()
  await expect(cy.getByTestId('pending-claim')).toHaveCount(0)
  await cy.getByTestId('mode-min-transfer').click()
  await expect(cy.getByTestId('no-transfers')).toHaveText('Everyone is settled. No transfers needed.')
  await cy.getByTestId('settle-done').click()
  await expect(cy.getByTestId('trip-position')).toContainText('All square')

  // Alice is square overall, though her rows still read "you owe Bob" and "Cy owes you": they
  // cancel. They stay in Settle up, faded, with nothing to act on — paying one would only open a
  // new debt — while the trip screen itself says only "All square", with no card repeating them.
  await alice.goto(tripUrl)
  await expect(alice.getByTestId('trip-position')).toContainText('All square')
  await expect(alice.getByTestId('who-owes')).toHaveCount(0)
  await alice.getByTestId('settle-up').click()
  const byPerson = alice.getByTestId('sheet-panel')
  await expect(byPerson.getByTestId('square-overall')).toContainText("You're square overall")
  await expect(byPerson.getByTestId('balance-row')).toHaveCount(2)
  await expect(byPerson.getByTestId('row-pay')).toHaveCount(0)
  await expect(byPerson.getByTestId('row-remind')).toHaveCount(0)
  await byPerson.getByTestId('settle-done').click()
  await expect(alice.getByTestId('sheet-panel')).toHaveCount(0)
  await expectRowsToSumToHero(alice)
})
