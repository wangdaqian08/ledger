import { expect, test } from '@playwright/test'
import { createTrip, signIn, typeAmount, uniquePerson } from './helpers'

/**
 * A trip's whole ending, end to end: end it, put it away, take it back out, delete it, restore it.
 *
 * The point of doing this against the real server is that every step is a different promise about
 * where the money went. Putting a trip away must not settle anything; deleting must take it from
 * everybody at once; restoring must bring the expenses back rather than an empty shell.
 */

/** Ends the trip through the host's own sheet, dismissing the confirm the way a thumb would. */
async function endTrip(page: import('@playwright/test').Page) {
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByTestId('appbar-action').click()
  await page.getByTestId('end-trip').click()
  await expect(page.getByTestId('trip-ended')).toBeVisible()
}

test('a host ends a trip, puts it away, and takes it back out', async ({ page }) => {
  await signIn(page, uniquePerson('Alice'))
  const tripUrl = await createTrip(page, 'Snow Trip')

  await page.getByTestId('add-expense').click()
  await typeAmount(page, '4000') // $40.00
  await page.getByTestId('expense-title').fill('Lift pass')
  await page.getByTestId('next-step').click()
  await page.getByTestId('save-expense').click()
  await expect(page.getByTestId('expense-row').filter({ hasText: 'Lift pass' })).toBeVisible()

  await endTrip(page)

  // Ended: it leaves the live list for the Completed section, still perfectly visible.
  await page.goto('/')
  await expect(page.getByTestId('completed-section').getByText('Snow Trip')).toBeVisible()

  await page.goto(tripUrl)
  await page.getByTestId('appbar-action').click()
  await page.getByTestId('put-away-trip').click()
  await expect(page.getByTestId('put-back-trip')).toBeVisible()

  // Put away: off the list entirely, until somebody asks for it by name.
  await page.goto('/')
  await expect(page.getByText('Snow Trip')).toHaveCount(0)
  const toggle = page.getByTestId('toggle-hidden')
  await expect(toggle).toContainText('1')
  await toggle.click()
  await expect(page.getByTestId('completed-section').getByText('Snow Trip')).toBeVisible()

  // …and it is a real trip behind that toggle, not a tombstone: the expense is still there.
  await page.getByTestId('group-card').filter({ hasText: 'Snow Trip' }).click()
  await expect(page.getByTestId('expense-row').filter({ hasText: 'Lift pass' })).toBeVisible()

  await page.getByTestId('appbar-action').click()
  await page.getByTestId('put-back-trip').click()
  await expect(page.getByTestId('put-away-trip')).toBeVisible()

  await page.goto('/')
  await expect(page.getByTestId('completed-section').getByText('Snow Trip')).toBeVisible()
  await expect(page.getByTestId('toggle-hidden')).toHaveCount(0)
})

test('deleting takes the trip from everyone, and restoring brings it back whole', async ({ browser }) => {
  const host = await browser.newContext({ viewport: { width: 390, height: 844 } })
  await host.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: 'http://localhost:5173' })
  const guest = await browser.newContext({ viewport: { width: 390, height: 844 } })
  const hostPage = await host.newPage()
  const guestPage = await guest.newPage()

  const alice = uniquePerson('Alice')
  const bob = uniquePerson('Bob')
  await signIn(hostPage, alice)
  await signIn(guestPage, bob)

  const tripUrl = await createTrip(hostPage, 'Doomed Trip')
  await hostPage.getByTestId('appbar-action').click()
  await hostPage.getByTestId('member-name').fill('Bob')
  await hostPage.getByTestId('add-member').click()
  await expect(hostPage.getByTestId('invite-member').filter({ hasText: 'Bob' })).toBeVisible()
  await hostPage.getByTestId('copy-link').click()
  // The note appearing is the copy confirmed; reading the clipboard before it is a race.
  await expect(hostPage.getByTestId('invite-note')).toBeVisible()
  const link = await hostPage.evaluate(() => navigator.clipboard.readText())
  expect(link, 'the invite link must carry its token in the fragment').toContain('#token=')
  await hostPage.getByTestId('sheet-close').click()

  // Bob joins, so the delete has somebody else to reach.
  await guestPage.goto(link)
  await guestPage.getByTestId('join-member').filter({ hasText: 'Bob' }).click()
  await guestPage.getByTestId('join-claim').click()
  await expect(guestPage).toHaveURL(/\/trips\//)

  await hostPage.getByTestId('add-expense').click()
  await typeAmount(hostPage, '6000') // $60.00, split two ways
  await hostPage.getByTestId('expense-title').fill('Cabin')
  await hostPage.getByTestId('next-step').click()
  await hostPage.getByTestId('save-expense').click()
  await expect(hostPage.getByTestId('expense-row').filter({ hasText: 'Cabin' })).toBeVisible()

  // The confirmation names what is unsettled — this is the last moment a warning can land.
  let asked = ''
  hostPage.once('dialog', (dialog) => {
    asked = dialog.message()
    return dialog.accept()
  })
  await hostPage.getByTestId('appbar-action').click()
  await hostPage.getByTestId('delete-trip').click()
  await expect(hostPage).toHaveURL(/\/$|\?/)
  expect(asked, 'the delete confirm must name the money still outstanding').toContain('30.00')

  // Off the host's list of trips — though still named in the bin below it, which is the point.
  await expect(hostPage.getByTestId('group-card').filter({ hasText: 'Doomed Trip' })).toHaveCount(0)
  await guestPage.goto('/')
  await expect(guestPage.getByText('Doomed Trip')).toHaveCount(0)
  // Gone means gone: the URL Bob still has in his history answers like a trip that never
  // existed — asserted on the words the screen actually shows for a 404.
  await guestPage.goto(tripUrl)
  await expect(guestPage.getByText('This trip is not here')).toBeVisible()

  // The bin, with its deadline, and the way back out of it.
  const binned = hostPage.getByTestId('deleted-trip').filter({ hasText: 'Doomed Trip' })
  await expect(binned).toBeVisible()
  await binned.getByTestId('restore-trip').click()
  await expect(hostPage.getByTestId('group-card').filter({ hasText: 'Doomed Trip' })).toBeVisible()
  await expect(hostPage.getByTestId('deleted-trip')).toHaveCount(0)

  // Restored whole, for its members too — the expense and the balance came back with it.
  await guestPage.goto('/')
  await expect(guestPage.getByTestId('group-card').filter({ hasText: 'Doomed Trip' })).toBeVisible()
  await guestPage.goto(tripUrl)
  await expect(guestPage.getByTestId('expense-row').filter({ hasText: 'Cabin' })).toBeVisible()

  await host.close()
  await guest.close()
})
