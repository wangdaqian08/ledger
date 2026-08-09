import { expect, test } from '@playwright/test'
import { addMembers, createTrip, expectRowsToSumToHero, signIn, typeAmount, uniquePerson } from './helpers'

/**
 * The founding scenario (spec §1–2), through the glass: a bill split three ways, a late arrival
 * written onto the roster and ticked onto the people list, and every share re-derived — no
 * rebalancing pass, no stored total to go stale.
 */
test('the hotel case: ticking a late arrival onto the bill re-divides it for everybody', async ({ page }) => {
  await signIn(page, uniquePerson('Alice'))
  await createTrip(page, 'Hotel weekend')
  await addMembers(page, ['Bob', 'Cara'])

  // $100.00 over three people: 33.34 + 33.33 + 33.33 — somebody carries the odd cent.
  await page.getByTestId('add-expense').click()
  await typeAmount(page, '10000')
  await page.getByTestId('expense-title').fill('Hotel deposit')
  await page.getByTestId('next-step').click()
  await page.getByTestId('save-expense').click()
  await expect(page.getByTestId('expense-row').filter({ hasText: 'Hotel deposit' })).toBeVisible()
  await expectRowsToSumToHero(page)

  // Dana arrives late: written onto the roster, then ticked onto the existing bill.
  await addMembers(page, ['Dana'])
  await page.getByTestId('expense-row').filter({ hasText: 'Hotel deposit' }).click()
  await page.getByTestId('edit-split-open').click()
  await page.getByTestId('person-toggle').filter({ hasText: 'Dana' }).click()
  await page.getByTestId('save-split').click()

  // 100.00 over four is 25.00 exactly — the corrected list flowed through to every share.
  await page.getByTestId('expense-row').filter({ hasText: 'Hotel deposit' }).click()
  const shares = page.getByTestId('split-row')
  await expect(shares).toHaveCount(4)
  for (let index = 0; index < 4; index += 1) {
    await expect(shares.nth(index)).toContainText('$25.00')
  }
  await page.getByTestId('sheet-close').click()
  await expectRowsToSumToHero(page)

  // The deposit turns out to have been $120.00, not $100.00. Correcting the amount re-derives
  // every share — 30.00 exactly, four ways — with nothing stored to go stale.
  await page.getByTestId('expense-row').filter({ hasText: 'Hotel deposit' }).click()
  await page.getByTestId('edit-split-open').click()
  // Tap the amount box: the keypad unfolds. Clear $100.00, key in $120.00.
  await page.getByTestId('edit-amount').click()
  for (let presses = 0; presses < 5; presses += 1) {
    await page.getByTestId('key-del').click()
  }
  await typeAmount(page, '12000')
  await expect(page.getByTestId('edit-amount')).toContainText('120.00')
  await page.getByTestId('save-split').click()

  await page.getByTestId('expense-row').filter({ hasText: 'Hotel deposit' }).click()
  await expect(shares).toHaveCount(4)
  for (let index = 0; index < 4; index += 1) {
    await expect(shares.nth(index)).toContainText('$30.00')
  }
  await page.getByTestId('sheet-close').click()
  await expectRowsToSumToHero(page)
})
