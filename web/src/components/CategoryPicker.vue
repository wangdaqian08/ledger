<script setup lang="ts">
import TallyIcon from './TallyIcon.vue'

export interface PickableCategory {
  id: string
  key: string
  nameEn: string
  nameZh: string
  icon: string
  hue: number
  builtIn: boolean
}

/**
 * Choosing what an expense was for. The eight built-ins plus whatever this trip has added, in the
 * order the API returns them — built-ins first, then the trip's own.
 */
defineProps<{
  categories: PickableCategory[]
  modelValue: string | null
  /** Which name to show. The interface is translated; a member's own category name is not. */
  locale?: 'en' | 'zh'
}>()

defineEmits<{ 'update:modelValue': [string] }>()
</script>

<template>
  <div class="picker" role="radiogroup">
    <button
      v-for="category in categories"
      :key="category.id"
      type="button"
      role="radio"
      :aria-checked="modelValue === category.id"
      class="picker__item"
      :class="{ 'picker__item--on': modelValue === category.id }"
      @click="$emit('update:modelValue', category.id)"
    >
      <span
        class="picker__disc"
        :style="{
          background: `var(--person-${((category.hue - 1) % 8) + 1})`,
          color: category.hue === 3 ? 'var(--ink)' : 'var(--text-on-accent)',
        }"
      >
        <TallyIcon :name="category.icon" :size="20" />
      </span>
      <span class="picker__name">{{ locale === 'zh' ? category.nameZh : category.nameEn }}</span>
    </button>
  </div>
</template>

<style scoped>
.picker {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.picker__item {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: var(--space-2);
  width: 72px;
  background: transparent;
  border: 2px solid transparent;
  border-radius: var(--radius-md);
  cursor: pointer;
}

.picker__item--on {
  border-color: var(--ink);
  background: var(--surface-card-hover);
}

.picker__disc {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: 2px solid var(--ink);
  border-radius: var(--radius-md);
}

.picker__name {
  font-size: var(--text-caption);
  font-weight: var(--weight-semibold);
  color: var(--text-body);
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}
</style>
