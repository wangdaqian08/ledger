<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { fractionDigits } from '@/lib/money'

/**
 * Typing an amount, in minor units all the way down.
 *
 * The model value is an integer number of cents and never anything else. The obvious alternative —
 * bind a decimal string, `parseFloat` it and multiply by 100 — is how `19.99` becomes `1998.9999`
 * and then, one `Math.round` later, an expense that is a cent short for reasons nobody can find.
 *
 * Digits are accumulated as an integer instead, the way a till does: every keystroke shifts left
 * and adds, so the value is exact at every point and the decimal point is only ever presentation.
 */
const props = withDefaults(
  defineProps<{
    modelValue: number
    currencyCode?: string
    symbol?: string
    placeholder?: string
    disabled?: boolean
  }>(),
  { currencyCode: 'AUD', symbol: '$', placeholder: '', disabled: false },
)

const emit = defineEmits<{ 'update:modelValue': [number] }>()

const digits = computed(() => fractionDigits(props.currencyCode))

/** What is shown while editing. Derived from the model, never parsed back into it. */
const text = ref(render(props.modelValue))

watch(
  () => [props.modelValue, props.currencyCode] as const,
  ([value]) => {
    if (toMinor(text.value) !== value) text.value = render(value)
  },
)

function render(amountMinor: number): string {
  if (amountMinor === 0) return ''
  const scale = 10 ** digits.value
  const abs = Math.abs(amountMinor)
  const remainder = abs % scale
  const whole = (abs - remainder) / scale
  return digits.value === 0 ? String(whole) : `${whole}.${String(remainder).padStart(digits.value, '0')}`
}

/** Reads the typed digits as an integer. No float ever exists, even briefly. */
function toMinor(shown: string): number {
  const onlyDigits = shown.replace(/\D/g, '')
  return onlyDigits === '' ? 0 : Number(onlyDigits)
}

function onInput(event: Event) {
  const raw = (event.target as HTMLInputElement).value
  const minor = toMinor(raw)

  // Cap rather than silently wrap: past this, integers stop being exact and the amount would drift.
  if (minor > Number.MAX_SAFE_INTEGER) return

  text.value = render(minor)
  emit('update:modelValue', minor)
}
</script>

<template>
  <label class="field">
    <span class="field__symbol">{{ symbol }}</span>
    <input
      class="field__input"
      type="text"
      inputmode="decimal"
      autocomplete="off"
      :value="text"
      :placeholder="placeholder"
      :disabled="disabled"
      @input="onInput"
    />
  </label>
</template>

<style scoped>
.field {
  display: inline-flex;
  align-items: baseline;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  background: var(--surface-card);
  border: 2px solid var(--ink);
  border-radius: var(--radius-md);
}

.field:focus-within {
  outline: 3px solid var(--focus-ring);
  outline-offset: 2px;
}

.field__symbol {
  font-family: var(--font-money);
  font-size: var(--text-money);
  font-weight: var(--weight-bold);
  color: var(--text-muted);
}

.field__input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-family: var(--font-money);
  font-variant-numeric: tabular-nums;
  font-size: var(--text-money-lg);
  font-weight: var(--weight-bold);
  color: var(--ink);
}
</style>
