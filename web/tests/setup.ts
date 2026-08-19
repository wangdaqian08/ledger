// Dates in these tests render through the machine's zone; pin it so a fixture stamped for
// one calendar day cannot drift to the day before on machines west of UTC.
process.env.TZ = 'UTC'

import { config } from '@vue/test-utils'
import { i18n } from '../src/i18n'

// happy-dom does not implement window.confirm; the sheets and destructive actions guard on it. A
// test that wants the cancel path stubs it to return false itself; the default is "yes, proceed".
if (typeof window !== 'undefined' && typeof window.confirm !== 'function') {
  window.confirm = () => true
}

/**
 * Every component mount gets the real i18n instance, so a component that reads a label through
 * `t()` renders the English string in tests rather than the raw key. Registered once here instead
 * of per-mount, because a component using i18n is now the norm, not the exception.
 */
config.global.plugins = [i18n]
