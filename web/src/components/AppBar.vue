<script setup lang="ts">
import TallyIcon from './TallyIcon.vue'

withDefaults(
  defineProps<{
    title: string
    back?: boolean
    /** A Lucide slug for the trailing action button. */
    action?: string
    /** What the action means to a human — the aria-label. Falls back to the slug. */
    actionLabel?: string
  }>(),
  {
    back: false,
    action: undefined,
  },
)

defineEmits<{ back: []; action: [] }>()
</script>

<template>
  <header class="bar">
    <button
      v-if="back"
      type="button"
      class="bar__icon"
      data-testid="appbar-back"
      aria-label="Back"
      @click="$emit('back')"
    >
      <TallyIcon name="arrow-left" :size="22" />
    </button>
    <h1 class="bar__title">{{ title }}</h1>
    <button
      v-if="action"
      type="button"
      class="bar__icon"
      data-testid="appbar-action"
      :aria-label="actionLabel ?? action"
      @click="$emit('action')"
    >
      <TallyIcon :name="action" :size="22" />
    </button>
  </header>
</template>

<style scoped>
.bar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background: var(--bg-app);
  border-bottom: 2px solid var(--ink);
  /* Sticks under the notch on a phone, which is where this app lives. */
  position: sticky;
  top: 0;
  padding-top: max(var(--space-3), env(safe-area-inset-top));
  z-index: 10;
}

.bar__title {
  flex: 1;
  min-width: 0;
  font-size: var(--text-heading-lg);
  font-weight: var(--weight-black);
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bar__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  background: var(--surface-card);
  border: 2px solid var(--ink);
  border-radius: var(--radius-circle);
  color: var(--ink);
  cursor: pointer;
}

.bar__icon:active {
  transform: translateY(2px);
}
</style>
