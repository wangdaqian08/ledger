<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AmountKeypadField from '@/components/AmountKeypadField.vue'
import SheetPanel from '@/components/SheetPanel.vue'
import TallyButton from '@/components/TallyButton.vue'
import TextField from '@/components/TextField.vue'
import { api } from '@/lib/api'
import { todayLocal } from '@/lib/dates'

/**
 * Screen 6 — "I paid you back": a claim against one bill, pre-filled with what is still owed.
 *
 * It is a request, not an act (§7a): nothing here moves a balance until the person owed agrees.
 * The screenshot slot arrives with build order step 7 (Cloud Storage), not here.
 */
const props = defineProps<{
  open: boolean
  itemId: string | null
  toName: string
  prefillMinor: number
  fromMemberId: string
  currencyCode: string
  symbol: string
}>()
const emit = defineEmits<{ close: []; saved: [] }>()

const { t } = useI18n()

const amountMinor = ref(0)
const paidOn = ref('')
const note = ref('')
const error = ref('')
const busy = ref(false)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    amountMinor.value = props.prefillMinor
    paidOn.value = todayLocal()
    note.value = ''
    error.value = ''
  },
)

async function send() {
  if (!props.itemId || amountMinor.value <= 0 || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await api.submitItemPayback(props.itemId, {
      fromMemberId: props.fromMemberId,
      amountMinor: amountMinor.value,
      paidOn: paidOn.value,
      note: note.value.trim() || undefined,
    })
    emit('saved')
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : String(failure)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <SheetPanel :open="open" :title="t('claim.title')" @close="emit('close')">
    <form class="claim" @submit.prevent="send">
      <label class="claim__label">{{ t('claim.amount') }}</label>
      <AmountKeypadField
        v-model="amountMinor"
        test-id="claim-amount"
        start-open
        :currency-code="currencyCode"
        :symbol="symbol"
      />

      <TextField v-model="paidOn" test-id="claim-date" type="date" :label="t('claim.date')" />
      <TextField v-model="note" :label="t('claim.note')" :placeholder="t('claim.notePlaceholder')" />

      <p class="claim__disclaimer">{{ t('claim.disclaimer', { name: toName }) }}</p>
      <p v-if="error" class="claim__error" role="alert">{{ error }}</p>

      <TallyButton
        type="submit"
        variant="primary"
        full-width
        data-testid="claim-send"
        :disabled="amountMinor <= 0 || busy"
        @click="send"
      >
        {{ t('claim.send') }}
      </TallyButton>
    </form>
  </SheetPanel>
</template>

<style scoped>
.claim {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.claim__label {
  font-size: var(--text-label);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--ls-label);
  text-transform: uppercase;
  color: var(--text-muted);
}

.claim__disclaimer {
  font-size: var(--text-caption);
  color: var(--text-muted);
  overflow-wrap: break-word;
}

.claim__error {
  color: var(--coral);
  font-size: var(--text-caption);
}
</style>
