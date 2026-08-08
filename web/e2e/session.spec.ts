import { expect, test } from '@playwright/test'
import { createTrip, signIn, uniquePerson } from './helpers'

test('signing out closes the door, and the back button cannot reopen it', async ({ page }) => {
  await signIn(page, uniquePerson('Alice'))
  await expect(page.getByTestId('sign-out')).toBeVisible()

  await page.getByTestId('sign-out').click()
  await expect(page).toHaveURL(/\/signin/)

  // Back navigates to the groups screen, whose first fetch is refused and bounces straight out.
  await page.goBack()
  await expect(page).toHaveURL(/\/signin/)
})

test('a 中文 browser gets the whole app in Chinese', async ({ browser }) => {
  const context = await browser.newContext({
    locale: 'zh-CN',
    viewport: { width: 390, height: 844 },
  })
  const page = await context.newPage()

  await page.goto('/signin')
  await expect(page.getByText('分账，搞定')).toBeVisible()
  await page.getByTestId('signin-name').fill(uniquePerson('小明'))
  await page.getByTestId('signin-submit').click()

  await expect(page.getByText('你的群组')).toBeVisible()
  await page.getByTestId('new-group').first().click()
  await page.getByTestId('group-name').fill('北海道')
  await page.getByTestId('create-group').click()

  await expect(page).toHaveURL(/\/trips\//)
  await expect(page.getByText('群组总支出')).toBeVisible()
  await expect(page.getByTestId('settle-up')).toContainText('结算')
})

test('the create-group screen refuses nothing silently', async ({ page }) => {
  // Not a business rule — a smoke check that the first screen a new person meets works alone:
  // sign in, make a group, land on it, all square with nobody else on it yet.
  await signIn(page, uniquePerson('Solo'))
  await createTrip(page, 'Just me')
  await expect(page.getByTestId('trip-position')).toContainText('All square')
})
