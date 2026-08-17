import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
// From vitest, not vite: the `test` block below is not part of Vite's own config type.
import { defineConfig } from 'vitest/config'

export default defineConfig({
  // '/' for dev, CI and tests. The production build sets VITE_BASE=/ledger/ — the app lives under
  // a sub-path on a host it shares, and this one value repoints assets, router and API calls.
  base: process.env.VITE_BASE ?? '/',
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    // The SPA and the API share an origin in production (spec §4), so there is no CORS anywhere in
    // the app. Proxying in dev keeps it that way rather than making the session cookie cross-site.
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: false } },
  },
  test: {
    environment: 'happy-dom',
    include: ['tests/**/*.spec.ts'],
    setupFiles: ['tests/setup.ts'],
  },
})
