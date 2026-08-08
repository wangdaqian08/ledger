import { expect, test, type Page } from '@playwright/test'
import { addMembers, createTrip, signIn, typeAmount, uniquePerson } from './helpers'

/**
 * This is a mobile web app: at phone width, nothing may ever force the page sideways. A
 * horizontal scrollbar on a 390px screen is a broken layout, wherever it comes from.
 */
async function expectNoSidewaysScroll(page: Page, moment: string) {
  const widths = await page.evaluate(() => ({
    scroll: document.documentElement.scrollWidth,
    client: document.documentElement.clientWidth,
  }))
  expect(widths.scroll, `${moment}: page must not scroll sideways`).toBeLessThanOrEqual(widths.client)
  // The page not scrolling is necessary but not sufficient: an element reaching past the
  // viewport inside an overflow-hidden ancestor is content the phone user simply cannot see.
  const poking = await page.evaluate(() => {
    const vw = document.documentElement.clientWidth
    const insideDeliberateScroller = (el: Element): boolean => {
      // A swipeable strip (category pages) legitimately keeps content off-screen; anything whose
      // ancestor scrolls sideways on purpose is that ancestor's business, not an overflow bug.
      for (let node = el.parentElement; node; node = node.parentElement) {
        const overflowX = getComputedStyle(node).overflowX
        if ((overflowX === 'auto' || overflowX === 'scroll') && node.scrollWidth > node.clientWidth) {
          return true
        }
      }
      return false
    }
    let worst: { right: number; what: string } | null = null
    for (const el of document.querySelectorAll('body *')) {
      const r = el.getBoundingClientRect()
      if (r.width > 0 && r.right > vw + 1 && (!worst || r.right > worst.right)) {
        if (insideDeliberateScroller(el)) continue
        worst = { right: Math.round(r.right), what: `${el.tagName}.${String(el.className).slice(0, 60)}` }
      }
    }
    return worst
  })
  expect(
    poking,
    `${moment}: nothing may reach past the ${await page.evaluate(() => document.documentElement.clientWidth)}px viewport`,
  ).toBeNull()
}

test('the settle-up flow never forces the page sideways', async ({ page }) => {
  await signIn(page, uniquePerson('Narrow'))
  await createTrip(page, 'Narrow lab')
  // A name as long as the seeded ones that first showed the cramping.
  await addMembers(page, ['Friend Number Ten'])

  // Bo owes the viewer, and the viewer owes Bo — both button states on one screen.
  await page.getByTestId('add-expense').click()
  await typeAmount(page, '5000')
  await page.getByTestId('expense-title').fill('Mine')
  await page.getByTestId('next-step').click()
  await page.getByTestId('save-expense').click()
  await expect(page.getByTestId('expense-row').filter({ hasText: 'Mine' })).toBeVisible()
  await expectNoSidewaysScroll(page, 'trip screen')

  await page.getByTestId('settle-up').click()
  const sheet = page.getByTestId('sheet-panel')
  await expect(sheet).toBeVisible()
  await expectNoSidewaysScroll(page, 'settle-up sheet')

  // Remind flips the button itself to a done state — no extra row, no wider layout.
  await sheet.getByTestId('row-remind').click()
  await expect(sheet.getByTestId('row-remind')).toBeVisible()
  await expectNoSidewaysScroll(page, 'after remind')
})

test('the pay form fits the phone', async ({ page }) => {
  await signIn(page, uniquePerson('Payer'))
  const url = await createTrip(page, 'Pay lab')
  await addMembers(page, ['Friend Number Ten'])

  // Bo fronts a bill, so the viewer owes and the row offers Pay.
  await page.getByTestId('add-expense').click()
  await typeAmount(page, '5000')
  await page.getByTestId('expense-title').fill('Theirs')
  await page.getByTestId('next-step').click()
  await page.getByTestId('payer-chip').filter({ hasText: 'Friend Number Ten' }).click()
  await page.getByTestId('save-expense').click()
  await expect(page.getByTestId('expense-row').filter({ hasText: 'Theirs' })).toBeVisible()

  await page.goto(url)
  await page.getByTestId('settle-up').click()
  const sheet = page.getByTestId('sheet-panel')
  await sheet.getByTestId('row-pay').click()
  await expect(sheet.getByTestId('pay-amount')).toHaveValue('25.00')
  await expectNoSidewaysScroll(page, 'pay form open')
})
