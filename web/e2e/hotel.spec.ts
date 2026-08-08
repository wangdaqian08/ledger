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
  await page.getByRole('button', { name: 'Add expense' }).click()
  await typeAmount(page, '10000')
  await page.getByLabel('What was it?').fill('Hotel deposit')
  await page.getByRole('button', { name: 'Next' }).click()
  await page.getByRole('button', { name: 'Save expense' }).click()
  await expect(page.getByRole('button', { name: /Hotel deposit/ })).toBeVisible()
  await expectRowsToSumToHero(page)

  // Dana arrives late: written onto the roster, then ticked onto the existing bill.
  await addMembers(page, ['Dana'])
  await page.getByRole('button', { name: /Hotel deposit/ }).click()
  await page.getByRole('button', { name: 'Fix the split' }).click()
  await page.locator('.edit__people .row', { hasText: 'Dana' }).click()
  await page.getByRole('button', { name: 'Save the split' }).click()

  // 100.00 over four is 25.00 exactly — the corrected list flowed through to every share.
  await page.getByRole('button', { name: /Hotel deposit/ }).click()
  const shares = page.locator('.detail__section', { hasText: 'How it was split' }).locator('.detail__row')
  await expect(shares).toHaveCount(4)
  for (let index = 0; index < 4; index += 1) {
    await expect(shares.nth(index)).toContainText('$25.00')
  }
  await page.getByRole('button', { name: 'Close' }).click()
  await expectRowsToSumToHero(page)
})
