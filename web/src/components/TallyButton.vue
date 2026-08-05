<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
    size?: 'sm' | 'md' | 'lg'
    disabled?: boolean
    fullWidth?: boolean
    type?: 'button' | 'submit'
  }>(),
  { variant: 'primary', size: 'md', disabled: false, fullWidth: false, type: 'button' },
)

defineEmits<{ click: [MouseEvent] }>()
</script>

<template>
  <button
    :type="type"
    :disabled="disabled"
    class="btn"
    :class="[`btn--${variant}`, `btn--${size}`, { 'btn--full': fullWidth }]"
    @click="$emit('click', $event)"
  >
    <slot />
  </button>
</template>

<style scoped>
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  border: 2px solid var(--ink);
  border-radius: var(--radius-md);
  font-family: var(--font-core);
  font-weight: var(--weight-bold);
  cursor: pointer;
  /* Tally presses translate rather than fade: the button moves onto the page under your thumb. */
  transition:
    transform var(--dur-fast) var(--ease-spring),
    background var(--dur-fast) var(--ease-out);
}

.btn:active:not(:disabled) {
  transform: translateY(2px);
}

.btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.btn--full {
  width: 100%;
}

.btn--sm {
  padding: var(--space-2) var(--space-3);
  font-size: var(--text-label);
}
.btn--md {
  padding: var(--space-3) var(--space-4);
  font-size: var(--text-body);
}
.btn--lg {
  padding: var(--space-4) var(--space-5);
  font-size: var(--text-body-lg);
}

.btn--primary {
  background: var(--action);
  color: var(--text-on-accent);
}
.btn--primary:hover:not(:disabled) {
  background: var(--action-press);
}

.btn--secondary {
  background: var(--surface-card);
  color: var(--text-body);
}
.btn--secondary:hover:not(:disabled) {
  background: var(--surface-card-hover);
}

.btn--ghost {
  background: transparent;
  border-color: transparent;
  color: var(--action);
}
.btn--ghost:hover:not(:disabled) {
  background: var(--surface-card-hover);
}

.btn--danger {
  background: var(--coral);
  color: var(--text-on-accent);
}
.btn--danger:hover:not(:disabled) {
  background: var(--coral-press);
}
</style>
