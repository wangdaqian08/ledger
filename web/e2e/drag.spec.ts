import { expect, test } from '@playwright/test'
import { addMembers, createTrip, signIn, typeAmount, uniquePerson } from './helpers'

/**
 * The SplitBar's whole reason to exist is the drag — and no test drove it with a real pointer
 * until a user reported it dead. The unit test that "covered" it tolerated silence
 * (`if (emitted)`), which is how a broken drag shipped.
 */
test('the custom split bar is draggable, and the amounts follow the handle', async ({ page }) => {
  await signIn(page, uniquePerson('Dragger'))
  await createTrip(page, 'Drag lab')
  await addMembers(page, ['Bo'])

  await page.getByRole('button', { name: 'Add expense' }).click()
  await typeAmount(page, '10000')
  await page.getByLabel('What was it?').fill('Weighted thing')
  await page.getByRole('button', { name: 'Next' }).click()
  await page.getByRole('button', { name: 'Custom' }).click()

  const bar = page.locator('.split__bar')
  await expect(bar).toBeVisible()
  const legendBefore = await page.locator('.split__legend').innerText()
  expect(legendBefore).toContain('50.00') // two people, weight 1:1, $100.00

  // A real drag: press on the handle, pull right in steps, release.
  const handle = page.locator('.split__handle-hit')
  const box = (await handle.boundingBox())!
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  await page.mouse.down()
  for (let step = 1; step <= 6; step += 1) {
    await page.mouse.move(box.x + box.width / 2 + step * 15, box.y + box.height / 2)
  }
  await page.mouse.up()

  const legendAfter = await page.locator('.split__legend').innerText()
  expect(legendAfter, 'dragging the handle must move the split').not.toBe(legendBefore)

  // And the moved amounts still account for every cent between the two of them.
  const amounts = [...legendAfter.matchAll(/\$(\d+)\.(\d{2})/g)].map((m) => Number(m[1]) * 100 + Number(m[2]))
  expect(amounts.reduce((a, b) => a + b, 0)).toBe(10_000)
})
