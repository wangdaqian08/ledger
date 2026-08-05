<script setup lang="ts">
withDefaults(
  defineProps<{
    modelValue: string
    label?: string
    placeholder?: string
    hint?: string
    error?: string
    type?: 'text' | 'email' | 'date'
    disabled?: boolean
  }>(),
  { label: undefined, placeholder: '', hint: undefined, error: undefined, type: 'text', disabled: false },
)

defineEmits<{ 'update:modelValue': [string] }>()
</script>

<template>
  <label class="field">
    <span v-if="label" class="field__label">{{ label }}</span>
    <input
      class="field__input"
      :class="{ 'field__input--bad': error }"
      :type="type"
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :aria-invalid="error ? 'true' : undefined"
      @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />
    <!-- The error replaces the hint rather than stacking under it: two lines of small print below
         a field is how people read neither. -->
    <span v-if="error" class="field__error">{{ error }}</span>
    <span v-else-if="hint" class="field__hint">{{ hint }}</span>
  </label>
</template>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field__label {
  font-size: var(--text-caption);
  font-weight: var(--weight-bold);
  color: var(--ink-2);
}

.field__input {
  padding: var(--space-3);
  background: var(--surface-card);
  border: 2px solid var(--ink);
  border-radius: var(--radius-md);
  font-family: var(--font-core);
  font-size: var(--text-body);
  color: var(--ink);
}

.field__input:disabled {
  opacity: 0.5;
}

.field__input--bad {
  border-color: var(--coral);
}

.field__hint {
  font-size: var(--text-caption);
  color: var(--text-muted);
}

.field__error {
  font-size: var(--text-caption);
  font-weight: var(--weight-semibold);
  color: var(--coral-press);
}
</style>
