import { expect, test, type Page } from '@playwright/test'
import { createTrip, expectRowsToSumToHero, signIn, typeAmount, uniquePerson } from './helpers'

/**
 * The two-party rule (§7a), with two real people in two real browsers: a claim moves nothing
 * while pending, a rejection says why and can be tried again, approval is what moves the money,
 * and an approved settlement can be undone — a settled trip can un-settle rather than trap a
 * wrong record.
 *
 * Along the way this walks the share link end to end: copied from the invite sheet, opened
 * signed-out, bounced through sign-in with the fragment-carried token intact, and claimed.
 */
test('pay → reject → try again → approve → undo, across two browsers', async ({ browser }) => {
  const contextA = await browser.newContext({ viewport: { width: 390, height: 844 } })
  await contextA.grantPermissions(['clipboard-read', 'clipboard-write'], {
    origin: 'http://localhost:5173',
  })
  const alice = await contextA.newPage()

  await signIn(alice, uniquePerson('Alice'))
  const tripUrl = await createTrip(alice, 'Two browsers')

  // Alice writes Bob down and copies the link for him.
  await alice.getByTestId('appbar-action').click()
  await alice.getByTestId('member-name').fill('Bob')
  await alice.getByTestId('add-member').click()
  await expect(alice.getByTestId('invite-member').filter({ hasText: 'Bob' })).toBeVisible()
  await alice.getByTestId('copy-link').click()
  await expect(alice.getByTestId('invite-note')).toBeVisible()
  const link = await alice.evaluate(() => navigator.clipboard.readText())
  expect(link).toContain('#token=')
  await alice.getByTestId('sheet-close').click()

  // Bob opens it signed out: bounced to sign-in, and the token in the fragment survives the
  // round trip — the part of the claim flow only a real browser can prove.
  const contextB = await browser.newContext({ viewport: { width: 390, height: 844 } })
  const bob = await contextB.newPage()
  await bob.goto(link)
  await expect(bob).toHaveURL(/\/signin/)
  await bob.getByTestId('signin-name').fill(uniquePerson('Bob'))
  await bob.getByTestId('signin-submit').click()
  await expect(bob.getByText('Pick your name')).toBeVisible()
  await bob.getByTestId('join-member').filter({ hasText: 'Bob' }).click()
  await bob.getByTestId('join-claim').click()
  await expect(bob).toHaveURL(/\/trips\//)

  // Alice fronts dinner for the two of them: she is owed $25.00.
  await alice.getByTestId('add-expense').click()
  await typeAmount(alice, '5000')
  await alice.getByTestId('expense-title').fill('Dinner')
  await alice.getByTestId('next-step').click()
  await alice.getByTestId('split-all').click()
  await alice.getByTestId('save-expense').click()
  await expect(alice.getByTestId('trip-position')).toContainText('You are owed')
  await expect(alice.getByTestId('trip-position')).toContainText('$25.00')
  await expectRowsToSumToHero(alice)

  // Bob claims he paid it back. Pending must move nothing on Alice's side.
  await bob.goto(tripUrl)
  await expect(bob.getByTestId('trip-position')).toContainText('You owe')
  await bob.getByTestId('expense-row').filter({ hasText: 'Dinner' }).click()
  await bob.getByTestId('pay-back-open').click()
  await expect(bob.getByTestId('claim-amount')).toContainText('25.00')
  await bob.getByTestId('claim-send').click()

  await alice.goto(tripUrl)
  await expect(alice.getByTestId('trip-position')).toContainText('$25.00', {
    timeout: 10_000,
  })
  await expectRowsToSumToHero(alice)

  // An item claim waits on the bill's own sheet, not the settle-up row — the trip payload
  // deliberately carries no paybacks (spec §6). Alice opens the bill, and there it is — the
  // decision is hers, so the badge names her, not the person who filed it.
  await alice.getByTestId('expense-row').filter({ hasText: 'Dinner' }).click()
  await expect(alice.getByTestId('sheet-panel')).toContainText('Waiting on you')
  await alice.getByTestId('reject-open').click()
  await alice.getByTestId('reject-reason').fill('nothing arrived')
  await alice.getByTestId('reject-send').click()
  await expect(alice.getByTestId('sheet-panel')).toContainText('Needs another look')
  await alice.getByTestId('sheet-close').click()

  await bob.goto(tripUrl)
  await bob.getByTestId('expense-row').filter({ hasText: 'Dinner' }).click()
  await expect(bob.getByTestId('sheet-panel')).toContainText('nothing arrived')

  // Bob tries again; this time Alice agrees, and the bill squares.
  await bob.getByTestId('pay-back-open').click()
  await bob.getByTestId('claim-send').click()

  await alice.goto(tripUrl)
  await alice.getByTestId('expense-row').filter({ hasText: 'Dinner' }).click()
  await alice.getByTestId('approve').click()
  await expect(alice.getByTestId('sheet-panel')).toContainText('All square')
  await alice.getByTestId('sheet-close').click()
  await expect(alice.getByTestId('trip-position')).toContainText('All square')
  await expectRowsToSumToHero(alice)

  // Deleting a bill with a confirmed repayment on it asks twice, and cold feet at the second
  // question — the one naming the money — leaves everything standing.
  await alice.getByTestId('expense-row').filter({ hasText: 'Dinner' }).click()
  const answers = [true, false]
  alice.on('dialog', (dialog) => (answers.shift() ? dialog.accept() : dialog.dismiss()))
  await alice.getByTestId('delete-item').click()
  await alice.getByTestId('sheet-close').click()
  await expect(alice.getByTestId('expense-row').filter({ hasText: 'Dinner' })).toBeVisible()
  await expect(alice.getByTestId('trip-position')).toContainText('All square')

  // And Bob can take it back: an approved repayment undone un-settles the trip (§7a). Undoing a
  // *confirmed* one asks first, since it re-opens a balance Alice thought was closed.
  await bob.goto(tripUrl)
  await bob.getByTestId('expense-row').filter({ hasText: 'Dinner' }).click()
  bob.once('dialog', (dialog) => dialog.accept())
  await approvedRowCancel(bob)
  await bob.getByTestId('sheet-close').click()

  await alice.goto(tripUrl)
  await expect(alice.getByTestId('trip-position')).toContainText('You are owed')
  await expect(alice.getByTestId('trip-position')).toContainText('$25.00')
  await expectRowsToSumToHero(alice)
})

/** The approved claim's Cancel — scoped past the rejected one's row, which has no button. */
async function approvedRowCancel(page: Page) {
  const approved = page.getByTestId('payback-row').filter({ hasText: 'Confirmed' })
  await approved.getByTestId('undo-claim').click()
}
