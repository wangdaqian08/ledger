<script setup lang="ts">
withDefaults(
  defineProps<{
    /** Greys out and sinks: how an all-square expense reads without being hidden. */
    sunk?: boolean
    interactive?: boolean
  }>(),
  { sunk: false, interactive: false },
)
</script>

<template>
  <div class="card" :class="{ 'card--sunk': sunk, 'card--interactive': interactive }">
    <slot />
  </div>
</template>

<style scoped>
.card {
  background: var(--surface-card);
  border: 2px solid var(--ink);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: var(--space-4);
  transition:
    transform var(--dur-fast) var(--ease-spring),
    box-shadow var(--dur-fast) var(--ease-out),
    opacity var(--dur-base) var(--ease-out);
}

.card--interactive {
  cursor: pointer;
}

.card--interactive:hover {
  background: var(--surface-card-hover);
}

.card--interactive:active {
  transform: translateY(2px);
  box-shadow: none;
}

.card--sunk {
  background: var(--bg-sunk);
  box-shadow: none;
  opacity: 0.72;
}
</style>
