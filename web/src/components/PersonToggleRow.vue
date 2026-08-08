<script setup lang="ts">
import PersonAvatar from './PersonAvatar.vue'
import AmountText from './AmountText.vue'

/**
 * One name on an item's people list, with a tick.
 *
 * This row is the primitive the whole design rests on: getting the list right is the entire fix
 * for the hotel case, so ticking somebody in or out has to be one obvious tap with no ceremony.
 */
withDefaults(
  defineProps<{
    displayName: string
    personHue: number
    selected: boolean
    /** Their share if the list stays as it is. Omitted before an amount has been entered. */
    shareMinor?: number | null
    currencyCode?: string
    symbol?: string
    disabled?: boolean
  }>(),
  { shareMinor: null, currencyCode: 'AUD', symbol: '$', disabled: false },
)

const emit = defineEmits<{ 'update:selected': [boolean] }>()

function toggle(current: boolean) {
  emit('update:selected', !current)
}
</script>

<template>
  <button
    type="button"
    class="row"
    :class="{ 'row--on': selected }"
    data-testid="person-toggle"
    :disabled="disabled"
    :aria-pressed="selected"
    @click="toggle(selected)"
  >
    <PersonAvatar :name="displayName" :hue="personHue" :size="36" :dimmed="!selected" />
    <span class="row__name">{{ displayName }}</span>
    <AmountText
      v-if="selected && shareMinor !== null"
      :amount-minor="shareMinor"
      :currency-code="currencyCode"
      :symbol="symbol"
      size="sm"
      :tone="'neutral'"
    />
    <span class="row__tick" :class="{ 'row__tick--on': selected }" aria-hidden="true">✓</span>
  </button>
</template>

<style scoped>
.row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-3);
  background: var(--surface-card);
  border: 2px solid var(--hairline-strong);
  border-radius: var(--radius-md);
  cursor: pointer;
  text-align: left;
  transition:
    border-color var(--dur-fast) var(--ease-out),
    background var(--dur-fast) var(--ease-out);
}

.row--on {
  border-color: var(--ink);
  background: var(--surface-card-hover);
}

.row:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.row__name {
  flex: 1;
  font-family: var(--font-core);
  font-weight: var(--weight-semibold);
  color: var(--text-body);
}

.row__tick {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: var(--radius-circle);
  border: 2px solid var(--hairline-strong);
  color: transparent;
  font-weight: var(--weight-black);
  font-size: 13px;
  transition: all var(--dur-fast) var(--ease-spring);
}

.row__tick--on {
  border-color: var(--ink);
  background: var(--mint);
  color: var(--text-on-accent);
}
</style>
