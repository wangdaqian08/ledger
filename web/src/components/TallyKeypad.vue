<script setup lang="ts">
import TallyIcon from './TallyIcon.vue'

/**
 * The on-screen number pad for entering an amount on a phone.
 *
 * It emits **digits and a delete**, never a parsed value. The caller shifts them into an integer
 * of minor units the way a till does, which is why there is no decimal-point key: the point is
 * presentation, and offering it would invite somebody to type one and expect a float.
 */
import { KEYS, type KeypadKey } from '@/lib/till'

// Re-exported so callers importing the type alongside the component keep working.
export type { KeypadKey }

defineEmits<{ key: [KeypadKey] }>()
</script>

<template>
  <div class="pad">
    <button
      v-for="key in KEYS"
      :key="key"
      type="button"
      :data-testid="`key-${key}`"
      class="pad__key"
      :aria-label="key === 'del' ? 'Delete' : key"
      @click="$emit('key', key)"
    >
      <TallyIcon v-if="key === 'del'" name="delete" :size="22" />
      <template v-else>{{ key }}</template>
    </button>
  </div>
</template>

<style scoped>
.pad {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-3);
  width: 100%;
}

.pad__key {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 56px;
  background: var(--surface-card);
  border: 2px solid var(--ink);
  border-radius: var(--radius-md);
  box-shadow: 0 3px 0 0 var(--ink);
  font-family: var(--font-money);
  font-size: var(--text-money);
  font-weight: var(--weight-bold);
  color: var(--ink);
  cursor: pointer;
  transition: transform var(--dur-fast) var(--ease-spring);
}

.pad__key:active {
  transform: translateY(3px);
  box-shadow: none;
}
</style>
