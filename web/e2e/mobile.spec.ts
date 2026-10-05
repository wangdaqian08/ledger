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
  await page.getByTestId('split-all').click()
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
  await page.getByTestId('split-all').click()
  await page.getByTestId('payer-chip').filter({ hasText: 'Friend Number Ten' }).click()
  await page.getByTestId('save-expense').click()
  await expect(page.getByTestId('expense-row').filter({ hasText: 'Theirs' })).toBeVisible()

  await page.goto(url)
  await page.getByTestId('settle-up').click()
  const sheet = page.getByTestId('sheet-panel')
  await sheet.getByTestId('row-pay').click()
  await expect(sheet.getByTestId('pay-amount')).toContainText('25.00')
  await expectNoSidewaysScroll(page, 'pay form open')
})

test('"How it adds up" fits the phone, at its widest: Settled column and five-figure amounts', async ({
  page,
}) => {
  await signIn(page, uniquePerson('Sums'))
  await createTrip(page, 'Sums lab')
  await addMembers(page, ['Friend Number Ten'])

  // The friend fronts a five-figure bill split evenly — $6,172.83 each, no odd cent — so the money
  // columns carry the widest figures a friends' trip is likely to see.
  await page.getByTestId('add-expense').click()
  await typeAmount(page, '1234566')
  await page.getByTestId('expense-title').fill('Chalet')
  await page.getByTestId('next-step').click()
  await page.getByTestId('split-all').click()
  await page.getByTestId('payer-chip').filter({ hasText: 'Friend Number Ten' }).click()
  await page.getByTestId('save-expense').click()
  await expect(page.getByTestId('expense-row').filter({ hasText: 'Chalet' })).toBeVisible()

  // The viewer pays them back and, as the creator, confirms it for a friend who has never signed in
  // and so cannot (§3) — which puts something in Settled and grows the table its fourth money column.
  await page.getByTestId('settle-up').click()
  const sheet = page.getByTestId('sheet-panel')
  await sheet.getByTestId('row-pay').click()
  await expect(sheet.getByTestId('pay-amount')).toContainText('6,172.83')
  await sheet.getByTestId('pay-send').click()
  await sheet.getByTestId('pending-claim').getByTestId('pending-approve').click()
  await expect(sheet.getByTestId('settled-claim')).toBeVisible()

  await sheet.getByTestId('breakdown-toggle').click()
  const table = sheet.getByTestId('breakdown')
  await expect(table).toBeVisible()
  await expect(table.getByTestId('breakdown-head-settled')).toBeVisible()
  await expect(table.getByTestId('breakdown-row')).toHaveCount(2)
  await expect(table.getByTestId('breakdown-total').getByTestId('breakdown-paid')).toHaveText('$12,345.66')
  await table.scrollIntoViewIfNeeded()
  await expectNoSidewaysScroll(page, 'how it adds up, expanded')

  await sheet
    .getByTestId('breakdown-toggle')
    .locator('svg')
    .evaluate((icon: SVGElement) => {
      icon.style.transition = 'none'
      icon.style.transform = 'rotate(45deg)'
    })
  // Necessary, not sufficient: the sheet's body scrolls, so a table too wide for it would scroll
  // sideways *inside the sheet* — which the check above deliberately forgives as a scroller. So the
  // table answers for itself: no box from it up to the sheet may scroll sideways, and no figure may
  // spill out of its own column over the next one.
  const spill = await table.evaluate((root) => {
    const sideways: string[] = []
    for (let node: Element | null = root; node; node = node.parentElement) {
      if (node.scrollWidth > node.clientWidth + 1) sideways.push(`${node.tagName}.${node.className}`)
      if (node.getAttribute('data-testid') === 'sheet-panel') break
    }
    const columns = ['paid', 'share', 'settled', 'balance', 'transfers']
    const cells = root.querySelectorAll(columns.map((c) => `[data-testid="breakdown-${c}"]`).join(','))
    const spilling = Array.from(cells)
      .filter((cell) => cell.scrollWidth > cell.clientWidth + 1)
      .map((cell) => `${cell.getAttribute('data-testid')}: ${cell.textContent?.trim()}`)
    return { sideways, spilling, cells: cells.length }
  })
  // Two people and the totals, five figures each: proof the selectors found the table at all.
  expect(spill.cells).toBe(15)
  expect(spill.sideways, 'nothing from the table up to the sheet may scroll sideways').toEqual([])
  expect(spill.spilling, 'every figure must fit its own column').toEqual([])
})

test('a long PayID wraps inside the phone, on the roster and in the transfer plan', async ({ page }) => {
  await signIn(page, uniquePerson('Wrap'))
  await createTrip(page, 'Wrap lab')
  await addMembers(page, ['Friend Number Ten'])

  // The viewer fronts a bill, so the plan has the friend paying them — their own PayID on show.
  await page.getByTestId('add-expense').click()
  await typeAmount(page, '5000')
  await page.getByTestId('expense-title').fill('Mine')
  await page.getByTestId('next-step').click()
  await page.getByTestId('split-all').click()
  await page.getByTestId('save-expense').click()
  await expect(page.getByTestId('expense-row').filter({ hasText: 'Mine' })).toBeVisible()

  // No spaces and no hyphens: nothing for the browser to break on but overflow-wrap itself.
  const longPayId = `${'x'.repeat(120)}@example.com`
  await page.getByTestId('appbar-action').click()
  await page.getByTestId('payid-add').click()
  await page.getByTestId('payid-input').fill(longPayId)
  await page.getByTestId('payid-save').click()
  await expect(page.getByTestId('payid-value')).toHaveText(longPayId)
  await expectNoSidewaysScroll(page, 'roster with a long PayID')
  await page.getByTestId('sheet-close').click()

  await page.getByTestId('settle-up').click()
  await page.getByTestId('mode-min-transfer').click()
  await expect(page.getByTestId('transfer-row')).toHaveCount(1)
  await expect(page.getByTestId('transfer-row').getByTestId('payid-value')).toHaveText(longPayId)
  await expectNoSidewaysScroll(page, 'transfer plan with a long PayID')
})
