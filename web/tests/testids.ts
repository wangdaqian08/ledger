import type { DOMWrapper, VueWrapper } from '@vue/test-utils'

/**
 * Selection by `data-testid`, the suite's convention: tests grab the contract points components
 * declare, not tag names, classes, or display copy — all three of which change for reasons that
 * have nothing to do with behaviour (styling passes, i18n, copy edits).
 *
 * Repeated elements share one id (`person-toggle`, `expense-row`, …) and are told apart by
 * content, mirroring Playwright's `getByTestId(...).filter(...)` on the e2e side.
 */
export const testId = (id: string) => `[data-testid="${id}"]`

export function findByTestId(wrapper: VueWrapper<unknown>, id: string): DOMWrapper<Element> {
  return wrapper.find(testId(id))
}

export function findAllByTestId(wrapper: VueWrapper<unknown>, id: string): DOMWrapper<Element>[] {
  return wrapper.findAll(testId(id))
}
