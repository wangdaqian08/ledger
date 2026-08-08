import { expect, test } from '@playwright/test'
import { createTrip, signIn, uniquePerson } from './helpers'

test('signing out closes the door, and the back button cannot reopen it', async ({ page }) => {
  await signIn(page, uniquePerson('Alice'))
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()

  await page.getByRole('button', { name: 'Sign out' }).click()
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
  await page.getByPlaceholder('你的名字').fill(uniquePerson('小明'))
  await page.getByRole('button', { name: '登录' }).click()

  await expect(page.getByText('你的群组')).toBeVisible()
  await page.getByRole('button', { name: '新建群组' }).first().click()
  await page.getByLabel('群组名称').fill('北海道')
  await page.getByRole('button', { name: '创建群组' }).click()

  await expect(page).toHaveURL(/\/trips\//)
  await expect(page.getByText('群组总支出')).toBeVisible()
  await expect(page.getByRole('button', { name: '结算' })).toBeVisible()
})

test('the create-group screen refuses nothing silently', async ({ page }) => {
  // Not a business rule — a smoke check that the first screen a new person meets works alone:
  // sign in, make a group, land on it, all square with nobody else on it yet.
  await signIn(page, uniquePerson('Solo'))
  await createTrip(page, 'Just me')
  await expect(page.locator('.trip__hero-position')).toContainText('All square')
})
