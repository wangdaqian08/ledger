<script setup lang="ts">
withDefaults(defineProps<{ tone?: 'neutral' | 'owed' | 'owe' | 'settled' | 'pending' }>(), {
  tone: 'neutral',
})
</script>

<template>
  <span class="badge" :class="`badge--${tone}`"><slot /></span>
</template>

<style scoped>
.badge {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: 2px var(--space-2);
  border-radius: var(--radius-pill);
  font-family: var(--font-core);
  font-size: var(--text-caption);
  font-weight: var(--weight-bold);
  line-height: 1.4;
  white-space: nowrap;
}

.badge--neutral {
  background: var(--bg-sunk);
  color: var(--text-muted);
}
.badge--owed {
  background: var(--mint-tint);
  color: var(--mint-press);
}
.badge--owe {
  background: var(--coral-tint);
  color: var(--coral-press);
}
.badge--settled {
  background: var(--mint-tint);
  color: var(--mint-press);
}
/* Pending is deliberately not a money colour: nothing has moved yet. */
.badge--pending {
  background: var(--lemon-tint);
  color: var(--ink-2);
}
</style>
