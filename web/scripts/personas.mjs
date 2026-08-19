// Open N isolated Playwright browser windows, all pointed at the app. Each window is its own
// session (separate cookie jar), so each can be a different person. Nothing is automated — you
// create a group in one, copy the invite link, paste it into another, join, and test by hand.
// Your normal Chrome can be a person too; just sign in there as a fourth name.
//
//   node scripts/personas.mjs        # 3 windows (default) — your Chrome makes 4
//   node scripts/personas.mjs 4      # 4 windows, if you don't want to use Chrome
//
// Ctrl-C closes them all.

/* global process, console */
import { chromium } from '@playwright/test'

const BASE = process.env.BASE ?? 'http://localhost:5173'
const count = Number.parseInt(process.argv[2] ?? '3', 10) || 3

const browser = await chromium.launch({ headless: false })

const size = { w: 460, h: 900, gap: 12 }
for (let i = 0; i < count; i += 1) {
    const context = await browser.newContext({ viewport: null })
    const page = await context.newPage()
    await page.goto(BASE)
    // Tile the windows left-to-right; harmless if the CDP call isn't available — just drag them.
    try {
        const cdp = await context.newCDPSession(page)
        const { windowId } = await cdp.send('Browser.getWindowForTarget')
        await cdp.send('Browser.setWindowBounds', {
            windowId,
            bounds: { left: i * (size.w + size.gap), top: 0, width: size.w, height: size.h },
        })
    } catch {
        /* tiling is optional */
    }
}

console.log(`\n${count} windows open at ${BASE}. Sign in as a different name in each.`)
console.log('Ctrl-C closes them all.\n')
await new Promise(() => {}) // keep the windows open
