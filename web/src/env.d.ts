/// <reference types="vite/client" />

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
