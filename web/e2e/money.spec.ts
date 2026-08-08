import { expect, test } from '@playwright/test'
import {
  addMembers,
  createTrip,
  expectRowsToSumToHero,
  parseCents,
  signIn,
  typeAmount,
  uniquePerson,
} from './helpers'

/** The client-minted id's whole point: what the sheet shows is what the server stores. */
test('the shares previewed while ticking people are exactly the shares that land', async ({ page }) => {
  await signIn(page, uniquePerson('Alice'))
  await createTrip(page, 'Preview weekend')
  await addMembers(page, ['Bob', 'Cara'])

  // $100.01 over three: the largest remainder hands two people 33.34 and one 33.33.
  await page.getByTestId('add-expense').click()
  await typeAmount(page, '10001')
  await page.getByTestId('expense-title').fill('Odd dinner')
  await page.getByTestId('next-step').click()

  const previewRows = page.getByTestId('person-toggle')
  await expect(previewRows).toHaveCount(3)
  const previewed: number[] = []
  for (let index = 0; index < 3; index += 1) {
    previewed.push(parseCents(await previewRows.nth(index).innerText()))
  }
  expect(previewed.reduce((a, b) => a + b, 0)).toBe(10_001)

  await page.getByTestId('save-expense').click()
  await expect(page.getByTestId('expense-row').filter({ hasText: 'Odd dinner' })).toBeVisible()

  // The saved bill's splits, person for person, are the numbers the sheet promised.
  await page.getByTestId('expense-row').filter({ hasText: 'Odd dinner' }).click()
  const savedRows = page.getByTestId('split-row')
  await expect(savedRows).toHaveCount(3)
  for (let index = 0; index < 3; index += 1) {
    const saved = parseCents(await savedRows.nth(index).innerText())
    expect(saved, `person ${index} must be charged what the preview showed`).toBe(previewed[index])
  }
  await page.getByTestId('sheet-close').click()
  await expectRowsToSumToHero(page)
})

test('saving twice cannot double an expense', async ({ page }) => {
  await signIn(page, uniquePerson('Alice'))
  await createTrip(page, 'Retry weekend')
  await addMembers(page, ['Bob'])

  await page.getByTestId('add-expense').click()
  await typeAmount(page, '5000')
  await page.getByTestId('expense-title').fill('Taxi')
  await page.getByTestId('next-step').click()

  // A double-tap on Save, as raw DOM events — a normal click would wait politely for the first
  // one's re-render, which is exactly what a double-tapping thumb does not do. The busy guard
  // and the client-minted id both stand in the way of a second expense.
  const save = page.getByTestId('save-expense')
  await save.dispatchEvent('click')
  await save.dispatchEvent('click')

  await expect(page.getByTestId('expense-row').filter({ hasText: 'Taxi' })).toHaveCount(1)
  await expectRowsToSumToHero(page)
})
