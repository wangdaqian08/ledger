<script setup lang="ts">
import { computed, ref } from 'vue'
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
 *
 * Eight tiles fit one screen; anything past them lives on further pages, swiped sideways with a
 * dot per page below — a thumb gesture, not a taller sheet. The pages snap, and the dots both
 * report and steer.
 */
const props = defineProps<{
  categories: PickableCategory[]
  modelValue: string | null
  /** Which name to show. The interface is translated; a member's own category name is not. */
  locale?: 'en' | 'zh'
}>()

defineEmits<{ 'update:modelValue': [string] }>()

const PAGE_SIZE = 8

const pages = computed(() => {
  const chunks: PickableCategory[][] = []
  for (let start = 0; start < props.categories.length; start += PAGE_SIZE) {
    chunks.push(props.categories.slice(start, start + PAGE_SIZE))
  }
  return chunks
})

const strip = ref<HTMLElement | null>(null)
const activePage = ref(0)

function onScroll() {
  const el = strip.value
  if (!el || el.clientWidth === 0) return
  activePage.value = Math.round(el.scrollLeft / el.clientWidth)
}

function goToPage(index: number) {
  activePage.value = index
  strip.value?.scrollTo({ left: index * strip.value.clientWidth, behavior: 'smooth' })
}
</script>

<template>
  <div class="picker">
    <div ref="strip" class="picker__strip" data-testid="category-strip" @scroll.passive="onScroll">
      <div v-for="(page, pageIndex) in pages" :key="pageIndex" class="picker__page" role="radiogroup">
        <button
          v-for="category in page"
          :key="category.id"
          type="button"
          role="radio"
          :aria-checked="modelValue === category.id"
          class="picker__item"
          data-testid="category-item"
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
    </div>

    <div v-if="pages.length > 1" class="picker__dots" data-testid="category-dots">
      <button
        v-for="(_page, pageIndex) in pages"
        :key="`dot-${pageIndex}`"
        type="button"
        class="picker__dot"
        data-testid="category-dot"
        :class="{ 'picker__dot--on': activePage === pageIndex }"
        :aria-label="`Categories page ${pageIndex + 1}`"
        :aria-current="activePage === pageIndex ? 'true' : undefined"
        @click="goToPage(pageIndex)"
      />
    </div>
  </div>
</template>

<style scoped>
.picker {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.picker__strip {
  display: flex;
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  scrollbar-width: none;
}

.picker__strip::-webkit-scrollbar {
  display: none;
}

.picker__page {
  min-width: 100%;
  scroll-snap-align: start;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-2);
}

.picker__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: var(--space-2);
  background: transparent;
  border: 2px solid transparent;
  border-radius: var(--radius-md);
  cursor: pointer;
}

.picker__item--on {
  border-color: var(--ink);
  background: var(--grape-tint);
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
  line-height: var(--lh-caption);
  /* Whole words, always: "Transport" must never read as "Trans…". A long custom name takes a
     second line rather than losing its ending. */
  overflow-wrap: break-word;
  max-width: 100%;
}

.picker__dots {
  display: flex;
  justify-content: center;
  gap: var(--space-2);
}

.picker__dot {
  width: 8px;
  height: 8px;
  padding: 0;
  border: 1.5px solid var(--hairline-strong);
  border-radius: var(--radius-circle);
  background: transparent;
  cursor: pointer;
}

.picker__dot--on {
  border-color: var(--ink);
  background: var(--ink);
}
</style>
