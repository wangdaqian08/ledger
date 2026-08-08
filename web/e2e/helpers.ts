import { expect, type Page } from '@playwright/test'

/**
 * The shared vocabulary of the e2e suite. Every test signs in as freshly minted people and builds
 * its own trip, so nothing here depends on the seeded demo data and tests cannot see each other.
 */

let counter = 0

/** A name no earlier test (or run) has used — the mock identity keys users on their name. */
export function uniquePerson(base: string): string {
  counter += 1
  return `${base} ${Date.now().toString(36)}${counter}`
}

export async function signIn(page: Page, name: string) {
  await page.goto('/signin')
  await page.getByPlaceholder('Your name').fill(name)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).toHaveURL(/\/$|\?/)
}

/** Creates a trip from the home screen and lands on it. Returns the trip's URL. */
export async function createTrip(page: Page, name: string): Promise<string> {
  await page.goto('/')
  await page.getByRole('button', { name: 'New group' }).first().click()
  await page.getByLabel('Group name').fill(name)
  await page.getByRole('button', { name: 'Create group' }).click()
  await expect(page).toHaveURL(/\/trips\//)
  return page.url()
}

/** Writes names onto the roster through the invite sheet, then closes it. */
export async function addMembers(page: Page, names: string[]) {
  await page.getByRole('button', { name: 'Invite' }).click()
  for (const name of names) {
    await page.getByPlaceholder('Add a name').fill(name)
    await page.getByRole('button', { name: 'Add', exact: true }).click()
    // The row appearing is the write confirmed — no timing guesswork.
    await expect(page.locator('.invite__member', { hasText: name })).toBeVisible()
  }
  await page.getByRole('button', { name: 'Close' }).click()
}

/** Types an amount on the keypad digit by digit, like a thumb would. */
export async function typeAmount(page: Page, digits: string) {
  for (const digit of digits) {
    await page.getByRole('button', { name: digit, exact: true }).click()
  }
}

/**
 * Invariant 2, read off the screen: the who-owes-who rows, signed by their captions, must sum to
 * the hero figure. If this fails the Settle-up screen is lying — the one failure the app cannot
 * survive — so every flow test calls it after every mutation.
 */
export async function expectRowsToSumToHero(page: Page) {
  const heroText = await page.locator('.trip__hero-position').innerText()
  const heroAmount = parseCents(heroText)
  // The label reaches the DOM uppercased by CSS; "ARE OWED" is checked first because
  // "YOU OWE" would otherwise never be reached.
  const upper = heroText.toUpperCase()
  const heroSign = upper.includes('ARE OWED') ? 1 : upper.includes('YOU OWE') ? -1 : 0

  const rows = page.locator('.trip__owes .row')
  const count = await rows.count()
  let sum = 0
  for (let index = 0; index < count; index += 1) {
    const text = await rows.nth(index).innerText()
    const amount = parseCents(text)
    if (text.includes('Owes you')) sum += amount
    else if (text.includes('You owe')) sum -= amount
  }

  expect(sum, `rows must sum to the hero: ${heroText}`).toBe(heroSign * heroAmount)
}

/** Reads the first money figure out of a rendered string, in integer cents. */
export function parseCents(text: string): number {
  const match = text.replace(/,/g, '').match(/\$ ?(\d+)\.(\d{2})/)
  if (!match) return 0
  return Number(match[1]) * 100 + Number(match[2])
}
