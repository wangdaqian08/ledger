<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
    size?: 'sm' | 'md' | 'lg'
    disabled?: boolean
    /** In flight: a spinner joins the label, the button reads as working, and a second tap is
     swallowed so a slow save cannot be double-submitted. */
    loading?: boolean
    fullWidth?: boolean
    type?: 'button' | 'submit'
  }>(),
  { variant: 'primary', size: 'md', disabled: false, loading: false, fullWidth: false, type: 'button' },
)

const emit = defineEmits<{ click: [MouseEvent] }>()

function onClick(event: MouseEvent) {
  // Disabled already blocks the pointer; loading has to block the synthetic click too, or a retry
  // on a flaky line fires the save a second time — the very thing the spinner is there to cover.
  if (props.disabled || props.loading) return
  emit('click', event)
}
</script>

<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    :aria-busy="loading || undefined"
    class="btn"
    :class="[`btn--${variant}`, `btn--${size}`, { 'btn--full': fullWidth, 'btn--loading': loading }]"
    @click="onClick"
  >
    <span v-if="loading" class="btn__spinner" data-testid="btn-spinner" aria-hidden="true" />
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

/* Loading reads as working, not inert: brighter than a plain disabled button so the spinner shows,
   with the busy cursor. Two classes (rather than :disabled) to match .btn:disabled's specificity and
   win on source order — and to key the rule off the class the template actually toggles, since a
   loading button is always disabled anyway. */
.btn.btn--loading {
  opacity: 0.7;
  cursor: progress;
}

.btn__spinner {
  width: 1em;
  height: 1em;
  flex: 0 0 auto;
  border: 2px solid currentColor;
  /* One transparent edge is what makes the rotation legible. */
  border-right-color: transparent;
  border-radius: var(--radius-circle);
  animation: btn-spin 0.6s linear infinite;
}

@keyframes btn-spin {
  to {
    transform: rotate(360deg);
  }
}

/* A reduced-motion preference still gets a turning spinner — the state has to stay visible — just
   slow enough not to trigger anyone; aria-busy carries the same news to assistive tech. */
@media (prefers-reduced-motion: reduce) {
  .btn__spinner {
    animation-duration: 1.6s;
  }
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
