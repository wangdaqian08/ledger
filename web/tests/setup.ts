import { config } from '@vue/test-utils'
import { i18n } from '../src/i18n'

/**
 * Every component mount gets the real i18n instance, so a component that reads a label through
 * `t()` renders the English string in tests rather than the raw key. Registered once here instead
 * of per-mount, because a component using i18n is now the norm, not the exception.
 */
config.global.plugins = [i18n]
