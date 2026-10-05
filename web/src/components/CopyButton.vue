<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import TallyButton from './TallyButton.vue'
import TallyIcon from './TallyIcon.vue'

/**
 * Puts a short piece of text on the clipboard, and says so for a moment.
 *
 * Always sits beside the value it copies, so a refusal (an insecure origin, a denied permission)
 * needs no error of its own: nothing flips, and the text is still right there to select by hand.
 * The "Copied" confirmation is visual on the button and spoken through a polite live region, since
 * a label that changes in place is news only to somebody who can see it.
 */
const props = defineProps<{
  text: string
  /** The accessible name — what is being copied, e.g. "Copy Cat's PayID". */
  label: string
  /** Lands on the button itself, where tests click. */
  testId: string
}>()

const { t } = useI18n()

const copied = ref(false)
let reset: ReturnType<typeof setTimeout> | undefined

async function copy() {
  try {
    await navigator.clipboard.writeText(props.text)
  } catch {
    return
  }
  copied.value = true
  clearTimeout(reset)
  reset = setTimeout(() => (copied.value = false), 2_000)
}

onBeforeUnmount(() => clearTimeout(reset))
</script>

<template>
   <span class="copy">
     <TallyButton size="sm" variant="secondary" :data-testid="testId" :aria-label="label" @click="copy">
       <TallyIcon :name="copied ? 'check' : 'copy'" :size="14" aria-hidden="true" />
       {{ copied ? t('common.copied') : t('common.copy') }}
     </TallyButton>
     <span class="copy__status" aria-live="polite">{{ copied ? t('common.copied') : '' }}</span>
   </span>
</template>

<style scoped>
.copy {
  display: inline-flex;
  flex: 0 0 auto;
}

/* Heard, not seen: the button already shows the change, so the announcement stays off-screen. */
.copy__status {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>
