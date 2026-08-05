<script setup lang="ts">
import TallyIcon from './TallyIcon.vue'

/** A small whole-number control — a weight on a split, a headcount. Never money. */
const props = withDefaults(
  defineProps<{ modelValue: number; min?: number; max?: number; suffix?: string }>(),
  { min: 0, max: 99, suffix: '' },
)

const emit = defineEmits<{ 'update:modelValue': [number] }>()

function set(next: number) {
  emit('update:modelValue', Math.min(props.max, Math.max(props.min, Math.trunc(next))))
}
</script>

<template>
  <div class="stepper">
    <button
      type="button"
      class="stepper__button"
      aria-label="Decrease"
      :disabled="modelValue <= min"
      @click="set(modelValue - 1)"
    >
      <TallyIcon name="minus" :size="18" />
    </button>
    <span class="stepper__value">{{ modelValue }}{{ suffix }}</span>
    <button
      type="button"
      class="stepper__button"
      aria-label="Increase"
      :disabled="modelValue >= max"
      @click="set(modelValue + 1)"
    >
      <TallyIcon name="plus" :size="18" />
    </button>
  </div>
</template>

<style scoped>
.stepper {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3);
}

.stepper__button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  background: var(--surface-card);
  border: 2px solid var(--ink);
  border-radius: var(--radius-circle);
  color: var(--ink);
  cursor: pointer;
}

.stepper__button:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.stepper__value {
  min-width: 44px;
  text-align: center;
  font-family: var(--font-money);
  font-variant-numeric: tabular-nums;
  font-size: var(--text-money-lg);
  font-weight: var(--weight-bold);
  color: var(--ink);
  line-height: 1;
}
</style>
