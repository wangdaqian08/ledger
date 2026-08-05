<script setup lang="ts">
import { computed } from 'vue'
import { ICON_INNER } from '@/lib/icons'

/**
 * A Lucide glyph, inlined as a real `<svg>` so it inherits `currentColor` and needs no network.
 *
 * `v-html` is normally a red flag; here the markup is a compile-time constant from a vendored
 * table and can never contain anything a user supplied. An unknown name draws the dashed
 * placeholder rather than a mystery blank.
 */
const props = withDefaults(defineProps<{ name: string; size?: number; strokeWidth?: number }>(), {
  size: 20,
  strokeWidth: 2,
})

const markup = computed(() => ICON_INNER[props.name] ?? null)
</script>

<template>
  <svg
    viewBox="0 0 24 24"
    :width="size"
    :height="size"
    role="img"
    :aria-label="name"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    class="icon"
  >
    <!--
      The only input is a key into ICON_INNER, a vendored compile-time table of Lucide markup; an
      unknown key renders the placeholder below instead. No caller-supplied string can reach this
      attribute, so there is nothing here for an attacker to control. If that ever stops being
      true — if icon markup started coming from the API, say — this must go back to a sanitiser
      or a component per glyph, and the rule below must be re-enabled.
    -->
    <!-- eslint-disable-next-line vue/no-v-html -->
    <g v-if="markup" v-html="markup" />
    <circle v-else cx="12" cy="12" r="9" stroke-dasharray="3 3" />
  </svg>
</template>

<style scoped>
.icon {
  display: block;
  flex: 0 0 auto;
}
</style>
