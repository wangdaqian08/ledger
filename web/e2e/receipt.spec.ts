import { expect, test } from '@playwright/test'
import { createTrip, signIn, typeAmount, uniquePerson } from './helpers'

/**
 * A receipt's whole life in a real browser — the one place the canvas downscale actually runs:
 * picked, re-encoded, saved behind the expense, pulled back through the authenticated image
 * endpoint, reviewed full screen, and finally the trip ended, which is what starts the 14-day
 * retention clock. (The sweep itself is server-tested; two weeks do not pass inside a test.)
 */
test('a receipt rides the expense, opens for review, and ending the trip closes the record', async ({
  page,
}) => {
  await signIn(page, uniquePerson('Recy'))
  await createTrip(page, 'Receipts')

  // A real JPEG, painted bigger than the 1600px limit so the client-side downscale genuinely runs.
  const dataUrl = await page.evaluate(() => {
    const canvas = document.createElement('canvas')
    canvas.width = 2400
    canvas.height = 1800
    const context = canvas.getContext('2d')!
    context.fillStyle = '#fffbf2'
    context.fillRect(0, 0, canvas.width, canvas.height)
    context.fillStyle = '#1a1720'
    context.font = '96px sans-serif'
    context.fillText('HOTEL RECEIPT — $42.00', 120, 240)
    return canvas.toDataURL('image/jpeg', 0.9)
  })
  const photo = Buffer.from(dataUrl.split(',')[1]!, 'base64')

  await page.getByTestId('add-expense').click()
  await typeAmount(page, '4200')
  await page.getByTestId('expense-title').fill('Hotel')
  await page.getByTestId('next-step').click()
  await page.getByTestId('split-all').click()
  await page
    .getByTestId('receipt-input')
    .setInputFiles({ name: 'bill.jpg', mimeType: 'image/jpeg', buffer: photo })
  await expect(page.getByTestId('receipt-preview')).toBeVisible()
  await page.getByTestId('save-expense').click()

  const row = page.getByTestId('expense-row').filter({ hasText: 'Hotel' })
  await expect(row).toBeVisible()

  // The thumbnail is the proof the upload landed: the <img> pulls the bytes back through the
  // session-authenticated endpoint, and only a decoded image reports a natural width.
  await row.click()
  const thumb = page.getByTestId('receipt-image')
  await expect(thumb).toBeVisible()
  await expect
    .poll(async () => thumb.evaluate((el) => (el as HTMLImageElement).naturalWidth))
    .toBeGreaterThan(0)

  // Tap to review full screen; Escape peels the review off and leaves the sheet standing.
  await page.getByTestId('receipt-thumb').click()
  const full = page.getByTestId('receipt-full')
  await expect(full).toBeVisible()
  await expect
    .poll(async () => full.evaluate((el) => (el as HTMLImageElement).naturalWidth))
    .toBeGreaterThan(0)
  await page.keyboard.press('Escape')
  await expect(page.getByTestId('receipt-lightbox')).toHaveCount(0)
  await expect(page.getByTestId('sheet-panel')).toBeVisible()
  await page.getByTestId('sheet-close').click()

  // The creator ends the trip. The confirm names the retention window; the badge appears and the
  // add button goes away — the spending record closes.
  await page.getByTestId('appbar-action').click()
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByTestId('end-trip').click()
  await expect(page.getByTestId('reopen-trip')).toBeVisible()
  await page.getByTestId('sheet-close').click()
  await expect(page.getByTestId('trip-ended')).toBeVisible()
  await expect(page.getByTestId('add-expense')).toHaveCount(0)

  // Still readable after the end — checking receipts while settling is what the window is for —
  // but no longer editable.
  await page.getByTestId('expense-row').filter({ hasText: 'Hotel' }).click()
  await expect(page.getByTestId('receipt-image')).toBeVisible()
  await expect(page.getByTestId('delete-item')).toHaveCount(0)
})
