import { expect, test } from '@playwright/test'
import { readFile } from 'node:fs/promises'
import { createTrip, signIn, typeAmount, uniquePerson } from './helpers'

/**
 * The expense export, end to end: the file a reviewer keeps must carry exactly what was entered.
 * One expense goes in through the real keypad; the CSV that comes back must hold its exact amount —
 * and nothing but expenses, whatever internal claims exist by then.
 */
test('the exported CSV holds the expense exactly as entered', async ({ page }) => {
  const alice = uniquePerson('Alice')
  await signIn(page, alice)
  await createTrip(page, 'Export me')

  await page.getByTestId('add-expense').click()
  await typeAmount(page, '100001') // $1,000.01 — a cent floats would smear
  await page.getByTestId('expense-title').fill('Odd hotel')
  await page.getByTestId('next-step').click()
  await page.getByTestId('split-all').click()
  await page.getByTestId('save-expense').click()
  await expect(page.getByTestId('expense-row').filter({ hasText: 'Odd hotel' })).toBeVisible()

  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByTestId('export-csv').click(),
  ])
  expect(download.suggestedFilename()).toContain('expenses.csv')

  const path = await download.path()
  const csv = (await readFile(path, 'utf-8')).replace(/^\uFEFF/, '')
  const lines = csv.split('\r\n').filter((line) => line.length > 0)

  expect(lines[0]).toBe('Date,Recorded at,Paid by,Item,Amount,Currency')
  expect(lines).toHaveLength(2) // header + the one expense
  expect(lines[1]).toContain(`,${alice},Odd hotel,1000.01,AUD`)
})
