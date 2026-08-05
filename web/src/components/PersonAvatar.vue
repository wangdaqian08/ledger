<script setup lang="ts">
import { computed } from 'vue'

/**
 * A person marker. Each member of a trip holds one of eight hues, assigned round-robin when they
 * join and never changed — the colour is part of how people recognise each other in the list.
 */
const props = withDefaults(
  defineProps<{
    name: string
    hue?: number
    size?: number
    selected?: boolean
    dimmed?: boolean
    /** One initial rather than two, for tight rows. */
    compact?: boolean
  }>(),
  { hue: 1, size: 40, selected: false, dimmed: false, compact: false },
)

const PERSON_HUES = 8

/** Wraps rather than clamps, so a tenth member gets hue 2 instead of everybody past eight sharing. */
const hue = computed(() => ((((Math.trunc(props.hue) - 1) % PERSON_HUES) + PERSON_HUES) % PERSON_HUES) + 1)

const initials = computed(() => {
  const parts = props.name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  const first = parts[0]![0]!
  if (props.compact || parts.length === 1) return first.toUpperCase()
  return (first + parts[1]![0]!).toUpperCase()
})

const fontSize = computed(() => Math.max(11, Math.round(props.size * (props.compact ? 0.44 : 0.38))))
</script>

<template>
  <span
    class="avatar"
    :class="{ 'avatar--selected': selected, 'avatar--dimmed': dimmed }"
    :style="{
      width: `${size}px`,
      height: `${size}px`,
      fontSize: `${fontSize}px`,
      background: `var(--person-${hue})`,
      // Hue 3 is the lemon; white on it is unreadable.
      color: hue === 3 ? 'var(--ink)' : 'var(--text-on-accent)',
    }"
    :title="name"
    >{{ initials }}</span
  >
</template>

<style scoped>
.avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border-radius: var(--radius-circle);
  border: 2px solid var(--ink);
  font-family: var(--font-core);
  font-weight: var(--weight-black);
  line-height: 1;
  letter-spacing: 0.01em;
  transition:
    opacity var(--dur-fast) var(--ease-out),
    box-shadow var(--dur-fast) var(--ease-spring),
    filter var(--dur-fast) var(--ease-out);
}

.avatar--selected {
  box-shadow:
    0 0 0 3px var(--paper),
    0 0 0 6px var(--ink);
}

.avatar--dimmed {
  opacity: 0.32;
  filter: saturate(0.4);
}
</style>
