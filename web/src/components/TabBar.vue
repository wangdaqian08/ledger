<script setup lang="ts">
import TallyIcon from './TallyIcon.vue'

export interface Tab {
  id: string
  label: string
  icon: string
}

defineProps<{ tabs: Tab[]; modelValue: string }>()
defineEmits<{ 'update:modelValue': [string]; add: [] }>()
</script>

<template>
  <nav class="tabs">
    <button
      v-for="tab in tabs"
      :key="tab.id"
      type="button"
      class="tabs__tab"
      :class="{ 'tabs__tab--on': tab.id === modelValue }"
      :aria-current="tab.id === modelValue ? 'page' : undefined"
      @click="$emit('update:modelValue', tab.id)"
    >
      <TallyIcon :name="tab.icon" :size="24" />
      <span class="tabs__label">{{ tab.label }}</span>
    </button>
  </nav>
</template>

<style scoped>
.tabs {
  display: flex;
  align-items: stretch;
  background: var(--surface-card);
  border-top: 2px solid var(--ink);
  /* Clears the home indicator; without this the last tab sits under it. */
  padding-bottom: env(safe-area-inset-bottom);
}

.tabs__tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  min-height: 56px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--ink-4);
  transition: color var(--dur-fast) var(--ease-out);
}

.tabs__tab--on {
  color: var(--action);
}

.tabs__tab--on :deep(.icon) {
  transform: translateY(-1px) scale(1.08);
  transition: transform var(--dur-fast) var(--ease-spring);
}

.tabs__label {
  font-size: 11px;
  font-weight: var(--weight-bold);
}
</style>
