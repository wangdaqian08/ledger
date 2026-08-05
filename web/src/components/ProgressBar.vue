<script setup lang="ts">
import { computed } from 'vue'

/**
 * How much of a bill has been paid back.
 *
 * The inputs are minor units and the only division is the one that turns them into a bar width —
 * a presentational percentage that is never rounded back into money. `coveredMinor` can exceed
 * `ofMinor` when somebody has overpaid, so the fill is clamped rather than allowed to overflow.
 */
const props = withDefaults(
  defineProps<{
    coveredMinor: number
    ofMinor: number
    tone?: 'action' | 'mint'
    height?: number
    label?: string
  }>(),
  { tone: 'mint', height: 14, label: undefined },
)

const percent = computed(() => {
  if (props.ofMinor <= 0) return 0
  return Math.min(100, Math.max(0, (props.coveredMinor / props.ofMinor) * 100))
})
</script>

<template>
  <div>
    <div v-if="label" class="bar__label">
      <span>{{ label }}</span>
      <span>{{ Math.round(percent) }}%</span>
    </div>
    <div
      class="bar"
      :style="{ height: `${height}px` }"
      role="progressbar"
      :aria-valuenow="coveredMinor"
      :aria-valuemin="0"
      :aria-valuemax="ofMinor"
    >
      <div class="bar__fill" :style="{ width: `${percent}%`, background: `var(--${tone})` }" />
    </div>
  </div>
</template>

<style scoped>
.bar__label {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: var(--text-caption);
  font-weight: var(--weight-bold);
  color: var(--ink-2);
}

.bar {
  background: var(--bg-sunk);
  border: 2px solid var(--ink);
  border-radius: var(--radius-pill);
  overflow: hidden;
}

.bar__fill {
  height: 100%;
  transition: width var(--dur-base) var(--ease-spring);
}
</style>
