<script setup lang="ts">
import { ref } from 'vue'
import TallyIcon from './TallyIcon.vue'
import TallyKeypad from './TallyKeypad.vue'
import { formatMinor } from '@/lib/money'
import { pressKey, type KeypadKey } from '@/lib/till'

/**
 * An amount you tap, and a keypad that unfolds beneath it.
 *
 * This app is tested and used as a web app on desktops as well as phones, where `inputmode`
 * summons no keyboard at all — and even on a phone, the OS keyboard is the wrong tool for a
 * till. The app's own keypad is the only way an amount is ever entered, everywhere, so the
 * digits always land the same way: shifted into whole cents, exact at every step.
 */
const props = withDefaults(
  defineProps<{
    modelValue: number
    currencyCode?: string
    symbol?: string
    /** Lands on the tappable box. The keypad's keys carry their own `key-*` ids. */
    testId?: string
    /** Open with the keypad already unfolded — for sheets whose whole point is this amount. */
    startOpen?: boolean
  }>(),
  { currencyCode: 'AUD', symbol: '$', testId: undefined, startOpen: false },
)

const emit = defineEmits<{ 'update:modelValue': [number] }>()

const open = ref(props.startOpen)

function onKey(key: KeypadKey) {
  emit('update:modelValue', pressKey(props.modelValue, key))
}
</script>

<template>
  <div class="tap">
    <button
      type="button"
      class="tap__box"
      :class="{ 'tap__box--open': open }"
      :data-testid="testId"
      :aria-expanded="open"
      @click="open = !open"
    >
      <span class="tap__amount">{{ formatMinor(modelValue, { currencyCode, symbol }) }}</span>
      <TallyIcon
        name="chevron-right"
        :size="18"
        class="tap__chevron"
        :class="{ 'tap__chevron--open': open }"
      />
    </button>

    <TallyKeypad v-if="open" @key="onKey" />
  </div>
</template>

<style scoped>
.tap {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.tap__box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  width: 100%;
  padding: var(--space-3) var(--space-4);
  background: var(--surface-card);
  border: 2px solid var(--ink);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.tap__box--open {
  background: var(--grape-tint);
}

.tap__amount {
  font-family: var(--font-money);
  font-variant-numeric: tabular-nums;
  font-size: var(--text-money-lg);
  font-weight: var(--weight-bold);
  color: var(--ink);
  white-space: nowrap;
}

.tap__chevron {
  transition: transform var(--dur-fast) var(--ease-out);
  color: var(--ink-2);
}

.tap__chevron--open {
  transform: rotate(90deg);
}
</style>
