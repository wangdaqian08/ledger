<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import TallyIcon from './TallyIcon.vue'

/**
 * The bottom sheet every "do something" flow in this app happens in.
 *
 * Escape and the scrim both close it, because a sheet you cannot dismiss is how somebody loses an
 * expense they were half way through typing. When there IS such work, `confirmClose` carries the
 * question to ask first, so a stray Escape or a click on the scrim cannot silently discard it.
 *
 * The dialog also owns the keyboard while it is up: focus moves in on open, Tab is trapped inside
 * (aria-modal promises the page behind is inert — this keeps that promise), and focus returns to
 * whatever opened it on close.
 */
const props = withDefaults(defineProps<{ open: boolean; title?: string; confirmClose?: string }>(), {
  title: undefined,
  confirmClose: undefined,
})
const emit = defineEmits<{ close: [] }>()
const { t } = useI18n()

const panel = ref<HTMLElement | null>(null)
let lastFocused: HTMLElement | null = null

/** A dismissal the user might not have meant asks first when `confirmClose` says there is work to lose. */
function requestClose() {
  if (props.confirmClose && !window.confirm(props.confirmClose)) return
  emit('close')
}

function focusables(): HTMLElement[] {
  if (!panel.value) return []
  const selector =
    'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
  return Array.from(panel.value.querySelectorAll<HTMLElement>(selector)).filter(
    (el) => el.offsetParent !== null,
  )
}

function onKeydown(event: KeyboardEvent) {
  if (!props.open) return
  if (event.key === 'Escape') {
    event.preventDefault()
    requestClose()
    return
  }
  if (event.key !== 'Tab') return
  const items = focusables()
  if (items.length === 0) return
  const first = items[0]!
  const last = items[items.length - 1]!
  const active = document.activeElement as HTMLElement | null
  if (event.shiftKey && (active === first || !panel.value?.contains(active))) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus()
  }
}

watch(
  () => props.open,
  (open) => {
    // Body scroll is locked while a sheet is up; without it the page behind scrolls under your thumb.
    document.body.style.overflow = open ? 'hidden' : ''
    if (open) {
      lastFocused = document.activeElement as HTMLElement | null
      nextTick(() => focusables()[0]?.focus())
    } else {
      lastFocused?.focus?.()
      lastFocused = null
    }
  },
)

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="sheet">
      <div class="sheet__scrim" @click="requestClose" />
      <section
        ref="panel"
        class="sheet__panel"
        role="dialog"
        data-testid="sheet-panel"
        aria-modal="true"
        :aria-label="title"
      >
        <header class="sheet__head">
          <span class="sheet__grip" aria-hidden="true" />
          <h2 v-if="title" class="sheet__title">{{ title }}</h2>
          <button
            type="button"
            class="sheet__close"
            data-testid="sheet-close"
            :aria-label="t('common.close')"
            @click="requestClose"
          >
            <TallyIcon name="x" :size="20" />
          </button>
        </header>
        <div class="sheet__body"><slot /></div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.sheet {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: flex-end;
}

.sheet__scrim {
  position: absolute;
  inset: 0;
  background: rgb(26 23 32 / 45%);
}

.sheet__panel {
  position: relative;
  width: 100%;
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-app);
  border-top: 2px solid var(--ink);
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  padding-bottom: env(safe-area-inset-bottom);
  animation: sheet-rise var(--dur-base) var(--ease-spring);
}

@keyframes sheet-rise {
  from {
    transform: translateY(100%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .sheet__panel {
    animation: none;
  }
}

.sheet__head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1.5px solid var(--hairline);
}

.sheet__grip {
  position: absolute;
  top: 8px;
  left: 50%;
  transform: translateX(-50%);
  width: 40px;
  height: 4px;
  border-radius: var(--radius-pill);
  background: var(--hairline-strong);
}

.sheet__title {
  flex: 1;
  font-size: var(--text-heading-lg);
  font-weight: var(--weight-black);
  color: var(--ink);
}

.sheet__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  background: transparent;
  border: 2px solid var(--hairline-strong);
  border-radius: var(--radius-circle);
  color: var(--ink-2);
  cursor: pointer;
}

.sheet__body {
  overflow-y: auto;
  padding: var(--space-4);
  /* A flex child's default min-height is its content, which quietly disables the max-height
     above: tall content poured out below the fold instead of scrolling in here. The amount
     display on "How much?" rendered 104px under the bottom of a phone because of this line
     being absent. */
  min-height: 0;
  flex: 1;
}
</style>
