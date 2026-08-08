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
  await alice.getByRole('button', { name: 'Invite' }).click()
  await alice.getByPlaceholder('Add a name').fill('Bob')
  await alice.getByRole('button', { name: 'Add', exact: true }).click()
  await expect(alice.locator('.invite__member', { hasText: 'Bob' })).toBeVisible()
  await alice.getByRole('button', { name: 'Copy invite link' }).click()
  await expect(alice.locator('.invite__note')).toBeVisible()
  const link = await alice.evaluate(() => navigator.clipboard.readText())
  expect(link).toContain('#token=')
  await alice.getByRole('button', { name: 'Close' }).click()

  // Bob opens it signed out: bounced to sign-in, and the token in the fragment survives the
  // round trip — the part of the claim flow only a real browser can prove.
  const contextB = await browser.newContext({ viewport: { width: 390, height: 844 } })
  const bob = await contextB.newPage()
  await bob.goto(link)
  await expect(bob).toHaveURL(/\/signin/)
  await bob.getByPlaceholder('Your name').fill(uniquePerson('Bob'))
  await bob.getByRole('button', { name: 'Sign in' }).click()
  await expect(bob.getByText('Pick your name')).toBeVisible()
  await bob.locator('.join__member', { hasText: 'Bob' }).click()
  await bob.getByRole('button', { name: "That's me" }).click()
  await expect(bob).toHaveURL(/\/trips\//)

  // Alice fronts dinner for the two of them: she is owed $25.00.
  await alice.getByRole('button', { name: 'Add expense' }).click()
  await typeAmount(alice, '5000')
  await alice.getByLabel('What was it?').fill('Dinner')
  await alice.getByRole('button', { name: 'Next' }).click()
  await alice.getByRole('button', { name: 'Save expense' }).click()
  await expect(alice.locator('.trip__hero-position')).toContainText('You are owed')
  await expect(alice.locator('.trip__hero-position')).toContainText('$25.00')
  await expectRowsToSumToHero(alice)

  // Bob claims he paid it back. Pending must move nothing on Alice's side.
  await bob.goto(tripUrl)
  await expect(bob.locator('.trip__hero-position')).toContainText('You owe')
  await bob.getByRole('button', { name: /Dinner/ }).click()
  await bob.getByRole('button', { name: 'Pay this back' }).click()
  await expect(bob.locator('.sheet__panel input').first()).toHaveValue('25.00')
  await bob.getByRole('button', { name: 'Send for confirmation' }).click()

  await alice.goto(tripUrl)
  await expect(alice.locator('.trip__hero-position')).toContainText('$25.00', {
    timeout: 10_000,
  })
  await expectRowsToSumToHero(alice)

  // An item claim waits on the bill's own sheet, not the settle-up row — the trip payload
  // deliberately carries no paybacks (spec §6). Alice opens the bill, and there it is.
  await alice.getByRole('button', { name: /Dinner/ }).click()
  await expect(alice.locator('.sheet__panel')).toContainText('Waiting for')
  await alice.getByRole('button', { name: 'Not yet' }).click()
  await alice.getByPlaceholder('Say why, so it can be put right').fill('nothing arrived')
  await alice.locator('.detail__reject').getByRole('button', { name: 'Not yet' }).click()
  await expect(alice.locator('.sheet__panel')).toContainText('Needs another look')
  await alice.getByRole('button', { name: 'Close' }).click()

  await bob.goto(tripUrl)
  await bob.getByRole('button', { name: /Dinner/ }).click()
  await expect(bob.locator('.sheet__panel')).toContainText('nothing arrived')

  // Bob tries again; this time Alice agrees, and the bill squares.
  await bob.getByRole('button', { name: 'Pay this back' }).click()
  await bob.getByRole('button', { name: 'Send for confirmation' }).click()

  await alice.goto(tripUrl)
  await alice.getByRole('button', { name: /Dinner/ }).click()
  await alice.getByRole('button', { name: 'Yes, paid me' }).click()
  await expect(alice.locator('.sheet__panel')).toContainText('All square')
  await alice.getByRole('button', { name: 'Close' }).click()
  await expect(alice.locator('.trip__hero-position')).toContainText('All square')
  await expectRowsToSumToHero(alice)

  // And Bob can take it back: an approved settlement undone un-settles the trip (§7a).
  await bob.goto(tripUrl)
  await bob.getByRole('button', { name: /Dinner/ }).click()
  await approvedRowCancel(bob)
  await bob.getByRole('button', { name: 'Close' }).click()

  await alice.goto(tripUrl)
  await expect(alice.locator('.trip__hero-position')).toContainText('You are owed')
  await expect(alice.locator('.trip__hero-position')).toContainText('$25.00')
  await expectRowsToSumToHero(alice)
})

/** The approved claim's Cancel — scoped past the rejected one's row, which has no button. */
async function approvedRowCancel(page: Page) {
  const approved = page.locator('.detail__payback', { hasText: 'Confirmed' })
  await approved.getByRole('button', { name: 'Cancel' }).click()
}
