<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { setLocale, SUPPORTED_LOCALES, type Locale } from '@/i18n'

/**
 * The language switch: one segment per language the app ships (SUPPORTED_LOCALES), the active one
 * lit. Words, not flag emoji — the design system carries no emoji (spec §5), and a flag names a
 * country, not a language (there is no flag for "English", and Chinese is spoken under several).
 * The choice is remembered across sessions; `setLocale` is what writes it.
 */
const { locale } = useI18n()

const LABEL: Record<Locale, string> = { en: 'EN', zh: '中文' }
</script>

<template>
  <div class="locale" role="group" aria-label="Language">
    <button
      v-for="option in SUPPORTED_LOCALES"
      :key="option"
      type="button"
      class="locale__seg"
      :class="{ 'locale__seg--on': locale === option }"
      :data-testid="`locale-${option}`"
      :aria-pressed="locale === option"
      @click="setLocale(option)"
    >
      {{ LABEL[option] }}
    </button>
  </div>
</template>

<style scoped>
.locale {
  display: inline-flex;
  flex: 0 0 auto;
  border: 2px solid var(--ink);
  border-radius: var(--radius-pill);
  overflow: hidden;
  background: var(--surface-card);
}

.locale__seg {
  padding: 4px 10px;
  font-size: var(--text-caption);
  font-weight: var(--weight-bold);
  line-height: 1.4;
  color: var(--text-muted);
  background: transparent;
  border: none;
  cursor: pointer;
}

.locale__seg--on {
  background: var(--grape-tint);
  color: var(--ink);
}

.locale__seg + .locale__seg {
  border-left: 2px solid var(--ink);
}

.locale__seg:active {
  transform: translateY(1px);
}
</style>
