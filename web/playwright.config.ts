import { defineConfig } from '@playwright/test'

/**
 * The end-to-end suite: the real Vue app against the real server against real Postgres.
 *
 * Both servers are started here — the seeded backend through Gradle (Testcontainers, so it needs
 * Docker and a couple of minutes of patience on a cold start) and Vite in front of it. The
 * backend entry waits on the port, not a URL, because every endpoint answers 401 to a stranger
 * and that is the correct behaviour, not unreadiness.
 *
 * One worker, deliberately: every test signs in as freshly minted people and builds its own trip,
 * but they all share one database, and a serial run keeps a failure's story readable.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:5173',
    viewport: { width: 390, height: 844 },
    trace: 'retain-on-failure',
  },
  webServer: [
    {
      command: 'cd .. && ./gradlew :server:bootTestRun --console=plain',
      port: 8080,
      timeout: 300_000,
      reuseExistingServer: !process.env.CI,
    },
    {
      command: 'npm run dev',
      port: 5173,
      timeout: 60_000,
      reuseExistingServer: !process.env.CI,
    },
  ],
})
