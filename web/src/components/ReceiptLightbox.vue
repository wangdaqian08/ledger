<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import TallyButton from './TallyButton.vue'
import TallyIcon from './TallyIcon.vue'

/**
 * The receipt, full screen — tapping the thumbnail opens the actual bill for review. Modelled on
 * SheetPanel's overlay duties but centred and dark, because the subject is a photograph, and one
 * layer above the sheet it opens over (z 200 over the sheet's 100).
 *
 * Deliberately not touching body scroll: this only ever opens over a sheet, which holds the lock
 * already — releasing it on close would unlock the page while the sheet is still up.
 */
const props = defineProps<{ open: boolean; src: string; canEdit: boolean; busy?: boolean }>()
const emit = defineEmits<{ close: []; replace: []; remove: [] }>()

const { t } = useI18n()

// Capture phase, so this runs before the sheet's own bubble-phase Escape handler — and stops
// there: Escape peels one layer, the review, never the sheet underneath it.
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.open) {
    event.stopPropagation()
    emit('close')
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown, true))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown, true))
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="lightbox" role="dialog" aria-modal="true" data-testid="receipt-lightbox">
      <div class="lightbox__scrim" @click="emit('close')" />
      <img class="lightbox__image" data-testid="receipt-full" :src="src" :alt="t('receipt.alt')" />
      <button
        type="button"
        class="lightbox__close"
        data-testid="lightbox-close"
        :aria-label="t('common.cancel')"
        @click="emit('close')"
      >
        <TallyIcon name="x" :size="20" />
      </button>
      <div v-if="canEdit" class="lightbox__actions">
        <TallyButton
          variant="secondary"
          size="sm"
          data-testid="receipt-replace"
          :disabled="busy"
          @click="emit('replace')"
        >
          {{ t('receipt.replace') }}
        </TallyButton>
        <TallyButton
          variant="danger"
          size="sm"
          data-testid="receipt-remove"
          :disabled="busy"
          @click="emit('remove')"
        >
          {{ t('receipt.remove') }}
        </TallyButton>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.lightbox {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-4);
}

/* Darker than a sheet's scrim: the page behind is context there, but here the photo is all. */
.lightbox__scrim {
  position: absolute;
  inset: 0;
  background: rgb(26 23 32 / 88%);
}

.lightbox__image {
  position: relative;
  max-width: 100%;
  max-height: calc(100% - var(--space-16));
  object-fit: contain;
  border-radius: var(--radius-sm);
  animation: lightbox-in var(--dur-fast) var(--ease-spring);
}

@keyframes lightbox-in {
  from {
    transform: scale(0.96);
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .lightbox__image {
    animation: none;
  }
}

.lightbox__close {
  position: absolute;
  top: calc(var(--space-4) + env(safe-area-inset-top, 0px));
  right: var(--space-4);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  background: var(--bg-app);
  border: 2px solid var(--ink);
  border-radius: var(--radius-circle);
  color: var(--ink);
  cursor: pointer;
}

.lightbox__actions {
  position: absolute;
  bottom: calc(var(--space-5) + var(--safe-bottom));
  display: flex;
  gap: var(--space-3);
}
</style>
