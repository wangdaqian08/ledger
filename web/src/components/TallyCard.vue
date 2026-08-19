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
  <!-- Interactive cards are the home screen's only way into a trip, so they are real buttons —
       focusable, and activated by Enter/Space — not click-only divs a keyboard cannot reach. -->
  <component
    :is="interactive ? 'button' : 'div'"
    :type="interactive ? 'button' : undefined"
    class="card"
    :class="{ 'card--sunk': sunk, 'card--interactive': interactive }"
  >
    <slot />
  </component>
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
  /* A <button> resets these; the card must still look and read like its content, full width. */
  width: 100%;
  font: inherit;
  color: inherit;
  text-align: left;
}

.card--interactive:hover {
  background: var(--surface-card-hover);
}

.card--interactive:focus-visible {
  outline: 3px solid var(--grape);
  outline-offset: 2px;
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
