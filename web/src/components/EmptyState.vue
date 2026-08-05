<script setup lang="ts">
import TallyIcon from './TallyIcon.vue'

withDefaults(defineProps<{ icon?: string; title: string; body?: string; action?: string }>(), {
  icon: 'receipt',
  body: undefined,
  action: undefined,
})

defineEmits<{ action: [] }>()
</script>

<template>
  <div class="empty">
    <span class="empty__disc"><TallyIcon :name="icon" :size="28" /></span>
    <h3 class="empty__title">{{ title }}</h3>
    <p v-if="body" class="empty__body">{{ body }}</p>
    <slot name="action">
      <button v-if="action" type="button" class="empty__action" @click="$emit('action')">
        {{ action }}
      </button>
    </slot>
  </div>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-5) var(--space-4);
  text-align: center;
}

.empty__disc {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  background: var(--bg-sunk);
  color: var(--ink-3);
  border: 2px solid var(--hairline-strong);
  border-radius: var(--radius-circle);
}

.empty__title {
  font-size: var(--text-heading-sm);
  font-weight: var(--weight-black);
  color: var(--ink);
}

.empty__body {
  font-size: var(--text-body);
  color: var(--text-muted);
  max-width: 32ch;
}

.empty__action {
  padding: var(--space-2) var(--space-4);
  background: var(--action);
  color: var(--text-on-accent);
  border: 2px solid var(--ink);
  border-radius: var(--radius-md);
  font-weight: var(--weight-bold);
  cursor: pointer;
}
</style>
