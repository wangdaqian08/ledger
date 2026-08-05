<script setup lang="ts">
import { computed } from 'vue'
import PersonAvatar from './PersonAvatar.vue'

export interface StackedPerson {
  id: string
  displayName: string
  personHue: number
}

const props = withDefaults(defineProps<{ people: StackedPerson[]; max?: number; size?: number }>(), {
  max: 4,
  size: 32,
})

const shown = computed(() => props.people.slice(0, props.max))
const overflow = computed(() => Math.max(0, props.people.length - props.max))
</script>

<template>
  <span class="stack">
    <PersonAvatar
      v-for="person in shown"
      :key="person.id"
      :name="person.displayName"
      :hue="person.personHue"
      :size="size"
      compact
      class="stack__item"
    />
    <span
      v-if="overflow > 0"
      class="stack__item stack__more"
      :style="{ width: `${size}px`, height: `${size}px` }"
      >+{{ overflow }}</span
    >
  </span>
</template>

<style scoped>
.stack {
  display: inline-flex;
  align-items: center;
}

/* Overlapped, and each one sits in front of the next so the leftmost reads first. */
.stack__item + .stack__item {
  margin-left: -10px;
}

.stack__more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-circle);
  border: 2px solid var(--ink);
  background: var(--bg-sunk);
  color: var(--text-muted);
  font-family: var(--font-core);
  font-weight: var(--weight-bold);
  font-size: 11px;
  line-height: 1;
}
</style>
