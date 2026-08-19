import { expect, test } from '@playwright/test'
import { addMembers, createTrip, signIn, typeAmount, uniquePerson } from './helpers'

/**
 * The "How much?" step must work one-handed on a phone with nothing hidden: the amount you are
 * typing stays on screen while you type it, and Next is reachable without scrolling. A keypad
 * whose result renders off-screen is a till with the display on the back.
 *
 * `toBeInViewport({ ratio: 1 })` retries, which also outlasts the sheet's rise animation — a
 * one-shot boundingBox read here once measured the sheet mid-flight and cried wolf.
 */
test('typing on the keypad shows the digits, with Next in reach, no scrolling', async ({ page }) => {
  await signIn(page, uniquePerson('Thumb'))
  await createTrip(page, 'Thumb lab')
  await addMembers(page, ['Bo'])

  await page.getByTestId('add-expense').click()
  await expect(page.getByTestId('amount-display')).toBeInViewport({ ratio: 1 })
  await expect(page.getByTestId('next-step')).toBeInViewport({ ratio: 1 })

  await typeAmount(page, '4250')
  await expect(page.getByTestId('amount-display')).toContainText('42.50')
  // Still true after typing: the display did not scroll away under the thumb.
  await expect(page.getByTestId('amount-display')).toBeInViewport({ ratio: 1 })
  await expect(page.getByTestId('next-step')).toBeInViewport({ ratio: 1 })

  // And the whole two-step flow still lands.
  await page.getByTestId('next-step').click()
  await page.getByTestId('split-all').click()
  await page.getByTestId('save-expense').click()
  await expect(page.getByTestId('expense-row')).toBeVisible()
})
