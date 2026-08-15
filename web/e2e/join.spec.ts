import { expect, test } from '@playwright/test'
import { createTrip, signIn, uniquePerson } from './helpers'

/**
 * The join link opened by somebody already aboard: the trip is offered, never a list of names
 * every one of which the server could only refuse with a 409. And the way out for the wrong
 * account — sign out, right on the card — must land the next person back on this same link,
 * token intact, because that round trip is exactly how one shared browser passes the phone
 * around a table.
 */
test('already aboard, the link offers the trip; sign-out hands it to the right person', async ({
  browser,
}) => {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
  await context.grantPermissions(['clipboard-read', 'clipboard-write'], {
    origin: 'http://localhost:5173',
  })
  const page = await context.newPage()

  const creator = uniquePerson('Jack')
  await signIn(page, creator)
  const tripUrl = await createTrip(page, 'Snow trip')

  await page.getByTestId('appbar-action').click()
  await page.getByTestId('member-name').fill('Friend2')
  await page.getByTestId('add-member').click()
  await expect(page.getByTestId('invite-member').filter({ hasText: 'Friend2' })).toBeVisible()
  await page.getByTestId('copy-link').click()
  await expect(page.getByTestId('invite-note')).toBeVisible()
  const link = await page.evaluate(() => navigator.clipboard.readText())
  expect(link).toContain('#token=')

  // The creator opens their own link — already aboard, so no names are offered at all.
  await page.goto(link)
  await expect(page.getByTestId('join-already')).toContainText(creator)
  await expect(page.getByTestId('join-member')).toHaveCount(0)

  // "Open" leads straight into the trip: the common real case of a re-tapped group-chat link.
  await page.getByTestId('join-open').click()
  await expect(page).toHaveURL(tripUrl)

  // Back on the link, the wrong-person exit: sign out on the spot, sign in as the friend, and
  // the fragment-carried token survives the whole round trip into a claim.
  await page.goto(link)
  await page.getByTestId('join-signout').click()
  await expect(page).toHaveURL(/\/signin/)
  await page.getByTestId('signin-name').fill(uniquePerson('Friend'))
  await page.getByTestId('signin-submit').click()

  await expect(page.getByText('Pick your name')).toBeVisible()
  await expect(page.getByTestId('join-who')).toContainText('Friend')
  await page.getByTestId('join-member').filter({ hasText: 'Friend2' }).click()
  await page.getByTestId('join-claim').click()
  await expect(page).toHaveURL(tripUrl)
})
