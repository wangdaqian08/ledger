/// <reference types="vite/client" />

/**
 * Baked in at build time by the deploy runbook (`VITE_APP_VERSION=$(git describe --tags --always)
 * npm run build`), so the corner of the sign-in screen can show what's actually deployed. Unset
 * locally — the component falls back to 'dev' rather than showing nothing.
 */
interface ImportMetaEnv {
  readonly VITE_APP_VERSION?: string
}

/**
 * TypeScript does not know what a `.vue` file or a side-effect `.css` import is on its own; these
 * tell it. Without them every component import in the app is a compile error.
 */
declare module '*.vue' {
  import type { DefineComponent } from 'vue'

  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}

declare module '*.css' {
  const content: string
  export default content
}
