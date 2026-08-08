<script setup lang="ts">
import { computed } from 'vue'
import { formatMinor } from '@/lib/money'

/**
 * Money. Always the money face, always tabular figures, sign carried by colour.
 *
 * Ported from Tally's `Amount` with one deliberate change: it takes **integer minor units**, not a
 * major-unit float. Tally did `Number(value).toLocaleString(...)`; doing that here would put a
 * floating-point number in the one place the whole application is trying to keep exact.
 */
const props = withDefaults(
  defineProps<{
    amountMinor: number
    currencyCode?: string
    symbol?: string
    size?: 'sm' | 'md' | 'lg' | 'hero'
    tone?: 'neutral' | 'owed' | 'owe' | 'settled' | 'onDark'
    showSign?: boolean
  }>(),
  {
    currencyCode: 'AUD',
    symbol: '$',
    size: 'md',
    tone: 'neutral',
    showSign: false,
  },
)

const text = computed(() =>
  formatMinor(props.amountMinor, {
    currencyCode: props.currencyCode,
    symbol: props.symbol,
    showSign: props.showSign,
  }),
)
</script>

<template>
  <span class="amount" :class="[`amount--${size}`, `amount--${tone}`]">{{ text }}</span>
</template>

<style scoped>
.amount {
  font-family: var(--font-money);
  font-variant-numeric: tabular-nums;
  font-feature-settings: 'tnum' 1;
  line-height: 1;
  white-space: nowrap;
  letter-spacing: -0.01em;
}

.amount--sm {
  font-size: var(--text-money-sm);
  font-weight: var(--weight-medium);
}
.amount--md {
  font-size: var(--text-money);
  font-weight: var(--weight-medium);
}
.amount--lg {
  font-size: var(--text-money-lg);
  font-weight: var(--weight-bold);
}
.amount--hero {
  font-size: var(--text-money-hero);
  font-weight: var(--weight-bold);
  letter-spacing: -0.03em;
  /* The tokens' own hero leading, not 1: at 48px a line box the size of the em leaves the
     digits' ink touching its very top edge, and the apex of a 1 reads as cut off. */
  line-height: var(--lh-hero);
}

.amount--neutral {
  color: var(--ink);
}
.amount--owed {
  color: var(--owed-to-you);
}
.amount--owe {
  color: var(--you-owe);
}
.amount--settled {
  color: var(--settled);
}
.amount--onDark {
  color: var(--text-on-accent);
}
</style>
