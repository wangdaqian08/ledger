<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'
import TallyIcon from './TallyIcon.vue'

/**
 * The bottom sheet every "do something" flow in this app happens in.
 *
 * Escape and the scrim both close it, because a sheet you cannot dismiss is how somebody loses an
 * expense they were half way through typing.
 */
const props = withDefaults(defineProps<{ open: boolean; title?: string }>(), { title: undefined })
const emit = defineEmits<{ close: [] }>()

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.open) emit('close')
}

// Body scroll is locked while a sheet is up; without it the page behind scrolls under your thumb.
watch(
  () => props.open,
  (open) => {
    document.body.style.overflow = open ? 'hidden' : ''
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
      <div class="sheet__scrim" @click="emit('close')" />
      <section class="sheet__panel" role="dialog" aria-modal="true" :aria-label="title">
        <header class="sheet__head">
          <span class="sheet__grip" aria-hidden="true" />
          <h2 v-if="title" class="sheet__title">{{ title }}</h2>
          <button type="button" class="sheet__close" aria-label="Close" @click="emit('close')">
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
}
</style>
