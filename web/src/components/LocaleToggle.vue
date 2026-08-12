<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { setLocale, SUPPORTED_LOCALES, type Locale } from '@/i18n'

/**
 * The language switch: one segment per language the app ships (SUPPORTED_LOCALES), the active one
 * lit. Shown as flags by product choice — a deliberate exception to the design system's no-emoji
 * rule. Each flag is a stand-in, not a claim: 🇬🇧 marks English (spoken well beyond Britain) and
 * 🇨🇳 marks 中文, so the accessible name carries the real language for anyone who can't see the
 * glyph, and note flag emoji fall back to letters on some platforms. The choice is remembered
 * across sessions; `setLocale` is what writes it.
 */
const { locale } = useI18n()

const FLAG: Record<Locale, string> = { en: '🇬🇧', zh: '🇨🇳' }
const NAME: Record<Locale, string> = { en: 'English', zh: '中文' }
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
      :aria-label="NAME[option]"
      :aria-pressed="locale === option"
      @click="setLocale(option)"
    >
      <span aria-hidden="true">{{ FLAG[option] }}</span>
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
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  font-size: 16px;
  line-height: 1.3;
  background: transparent;
  border: none;
  cursor: pointer;
  /* A dimmed flag reads as "not the current language"; the lit one comes back to full strength. */
  opacity: 0.45;
}

.locale__seg--on {
  background: var(--grape-tint);
  opacity: 1;
}

.locale__seg + .locale__seg {
  border-left: 2px solid var(--ink);
}

.locale__seg:active {
  transform: translateY(1px);
}
</style>
