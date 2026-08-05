<script setup lang="ts">
import AmountText from './AmountText.vue'
import AvatarStack, { type StackedPerson } from './AvatarStack.vue'
import TallyBadge from './TallyBadge.vue'
import TallyCard from './TallyCard.vue'
import TallyIcon from './TallyIcon.vue'

/**
 * The home screen's tile for one trip.
 *
 * Settled is `netMinor === 0`, full stop. Tally used `Math.abs(balance) < 0.005` because its
 * balances were floats and could not be compared to zero honestly; ours are whole cents, and a
 * tolerance here would mean calling somebody square while they still owed four cents.
 */
withDefaults(
  defineProps<{
    name: string
    icon?: string
    hue?: number
    members: StackedPerson[]
    yourNetMinor: number
    currencyCode?: string
    symbol?: string
  }>(),
  { icon: 'users', hue: 6, currencyCode: 'AUD', symbol: '$' },
)

defineEmits<{ click: [] }>()
</script>

<template>
  <TallyCard interactive class="group" @click="$emit('click')">
    <div class="group__head">
      <span
        class="group__disc"
        :style="{
          background: `var(--person-${hue})`,
          color: hue === 3 ? 'var(--ink)' : 'var(--text-on-accent)',
        }"
      >
        <TallyIcon :name="icon" :size="22" />
      </span>

      <div class="group__title">
        <div class="group__name">{{ name }}</div>
        <div class="group__count">{{ members.length }} {{ members.length === 1 ? 'person' : 'people' }}</div>
      </div>

      <TallyIcon name="chevron-right" :size="20" class="group__chevron" />
    </div>

    <div class="group__foot">
      <AvatarStack :people="members" :size="30" :max="5" />

      <TallyBadge v-if="yourNetMinor === 0" tone="settled">All square</TallyBadge>
      <span v-else class="group__balance">
        <span class="group__caption">{{ yourNetMinor > 0 ? 'you get' : 'you owe' }}</span>
        <AmountText
          :amount-minor="Math.abs(yourNetMinor)"
          :currency-code="currencyCode"
          :symbol="symbol"
          size="lg"
          :tone="yourNetMinor > 0 ? 'owed' : 'owe'"
        />
      </span>
    </div>
  </TallyCard>
</template>

<style scoped>
.group {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.group__head,
.group__foot {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.group__foot {
  justify-content: space-between;
}

.group__disc {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  flex: 0 0 auto;
  border: 2px solid var(--ink);
  border-radius: var(--radius-md);
}

.group__title {
  flex: 1;
  min-width: 0;
}

.group__name {
  font-size: var(--text-heading-lg);
  font-weight: var(--weight-black);
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.group__count {
  font-size: var(--text-caption);
  color: var(--text-muted);
  margin-top: 2px;
}

.group__chevron {
  color: var(--ink-4);
}

.group__balance {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.group__caption {
  font-size: 11px;
  font-weight: var(--weight-black);
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--text-subtle);
}
</style>
