import { expect, test } from '@playwright/test'
import { createTrip, expectRowsToSumToHero, signIn, typeAmount, uniquePerson } from './helpers'

/**
 * The settle-up loop, end to end, with two real people: Pay files a trip-level settlement, and the
 * person it is owed to approves it — on the settle-up strip itself, because a settlement has no bill
 * and so never appears on any item sheet. This is the loop that used to dead-end: the recipient had
 * no approve, reject or undo control anywhere, so the money could not move without the API.
 */
test('a trip-level settlement is approved by the person it is owed to, right on the strip', async ({
  browser,
}) => {
  const contextA = await browser.newContext({ viewport: { width: 390, height: 844 } })
  await contextA.grantPermissions(['clipboard-read', 'clipboard-write'], {
    origin: 'http://localhost:5173',
  })
  const alice = await contextA.newPage()

  await signIn(alice, uniquePerson('Alice'))
  const tripUrl = await createTrip(alice, 'Settle up')

  // Alice writes Bob down and copies the link for him.
  await alice.getByTestId('appbar-action').click()
  await alice.getByTestId('member-name').fill('Bob')
  await alice.getByTestId('add-member').click()
  await expect(alice.getByTestId('invite-member').filter({ hasText: 'Bob' })).toBeVisible()
  await alice.getByTestId('copy-link').click()
  await expect(alice.getByTestId('invite-note')).toBeVisible()
  const link = await alice.evaluate(() => navigator.clipboard.readText())
  await alice.getByTestId('sheet-close').click()

  const contextB = await browser.newContext({ viewport: { width: 390, height: 844 } })
  const bob = await contextB.newPage()
  await bob.goto(link)
  await expect(bob).toHaveURL(/\/signin/)
  await bob.getByTestId('signin-name').fill(uniquePerson('Bob'))
  await bob.getByTestId('signin-submit').click()
  await bob.getByTestId('join-member').filter({ hasText: 'Bob' }).click()
  await bob.getByTestId('join-claim').click()
  await expect(bob).toHaveURL(/\/trips\//)

  // Alice fronts $40 for the two of them, so Bob owes her $20.
  await alice.getByTestId('add-expense').click()
  await typeAmount(alice, '4000')
  await alice.getByTestId('category-item').first().click()
  await alice.getByTestId('expense-title').fill('Lunch')
  await alice.getByTestId('next-step').click()
  await alice.getByTestId('save-expense').click()
  await expect(alice.getByTestId('trip-position')).toContainText('$20.00')

  // Bob opens Settle up and pays Alice back with a trip-level settlement.
  await bob.goto(tripUrl)
  await expect(bob.getByTestId('trip-position')).toContainText('You owe')
  await bob.getByTestId('settle-up').click()
  await bob.getByTestId('sheet-panel').getByTestId('row-pay').click()
  await bob.getByTestId('pay-send').click()
  await expect(bob.getByTestId('pending-claim')).toBeVisible()
  await bob.getByTestId('settle-done').click()

  // Alice — the person owed — sees the claim with its amount, and approves it right there.
  await alice.goto(tripUrl)
  await alice.getByTestId('settle-up').click()
  const strip = alice.getByTestId('pending-claim')
  await expect(strip).toContainText('20.00')
  await expect(strip).toContainText('says they paid you')
  await strip.getByTestId('pending-approve').click()
  await alice.getByTestId('settle-done').click()

  // And the trip squares, from both chairs.
  await expect(alice.getByTestId('trip-position')).toContainText('All square')
  await expectRowsToSumToHero(alice)
  await bob.goto(tripUrl)
  await expect(bob.getByTestId('trip-position')).toContainText('All square')
})
