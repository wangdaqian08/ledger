<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AmountKeypadField from '@/components/AmountKeypadField.vue'
import BalanceRow from '@/components/BalanceRow.vue'
import SheetPanel from '@/components/SheetPanel.vue'
import TallyButton from '@/components/TallyButton.vue'
import { api, type SettlementRow } from '@/lib/api'

/**
 * Screen 7 — Settle up. One row per person; Pay files a trip-level settlement that stays
 * PENDING until the person owed (or the trip's creator) confirms it. "Done for now" closes the
 * sheet, because all-square is a derived state, never a button (§7a).
 */
const props = defineProps<{
  open: boolean
  tripId: string
  myMemberId: string
  rows: SettlementRow[]
  currencyCode: string
  symbol: string
}>()
const emit = defineEmits<{ close: []; changed: [] }>()

const { t } = useI18n()

const paying = ref<SettlementRow | null>(null)
const amountMinor = ref(0)
const error = ref('')
const busy = ref(false)
const reminded = ref<string | null>(null)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    paying.value = null
    error.value = ''
    reminded.value = null
  },
)

/** Claims between me and this row's person that are still waiting on somebody. */
const pendingOf = computed(() => (row: SettlementRow) => row.pending.filter((p) => p.status === 'PENDING'))

function startPay(row: SettlementRow) {
  paying.value = row
  // Positive owedMinor is "you owe them" — the amount the Pay button pre-fills.
  amountMinor.value = Math.max(0, row.owedMinor)
  error.value = ''
}

async function act(action: () => Promise<unknown>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await action()
    emit('changed')
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : String(failure)
  } finally {
    busy.value = false
  }
}

async function pay() {
  const row = paying.value
  if (!row || amountMinor.value <= 0) return
  await act(() =>
    api.submitSettlement(props.tripId, { toMemberId: row.memberId, amountMinor: amountMinor.value }),
  )
  if (!error.value) paying.value = null
}

async function remind(row: SettlementRow) {
  await act(() => api.remind(props.tripId, row.memberId))
  if (!error.value) reminded.value = row.memberId
}

const undoClaim = (paybackId: string) => act(() => api.undoPayback(paybackId))
</script>

<template>
  <SheetPanel :open="open" :title="t('trip.settleUp')" @close="emit('close')">
    <div class="settle">
      <div v-for="(row, index) in rows" :key="row.memberId" class="settle__entry">
        <!-- The API row says "positive = you owe them"; BalanceRow speaks the viewer's frame. -->
        <BalanceRow
          :display-name="row.displayName"
          :person-hue="row.personHue"
          :owed-minor="-row.owedMinor"
          :currency-code="currencyCode"
          :symbol="symbol"
          :pending="pendingOf(row).length > 0"
          :reminded="reminded === row.memberId"
          :divider="index < rows.length - 1"
          @pay="startPay(row)"
          @remind="remind(row)"
        />

        <div
          v-for="claim in pendingOf(row)"
          :key="claim.id"
          class="settle__pending"
          data-testid="pending-claim"
        >
          <span class="settle__pending-text">
            {{
              t('settle.sentForConfirmation', {
                name: claim.toMemberId === myMemberId ? 'you' : row.displayName,
              })
            }}
          </span>
          <TallyButton
            v-if="claim.fromMemberId === myMemberId"
            size="sm"
            variant="ghost"
            data-testid="pending-undo"
            @click="undoClaim(claim.id)"
          >
            {{ t('common.cancel') }}
          </TallyButton>
        </div>

        <form
          v-if="paying?.memberId === row.memberId"
          class="settle__pay"
          data-testid="pay-form"
          @submit.prevent="pay"
        >
          <AmountKeypadField
            v-model="amountMinor"
            test-id="pay-amount"
            :currency-code="currencyCode"
            :symbol="symbol"
          />
          <TallyButton
            type="submit"
            variant="primary"
            size="sm"
            data-testid="pay-send"
            :disabled="amountMinor <= 0 || busy"
            @click="pay"
          >
            {{ t('settle.pay') }}
          </TallyButton>
        </form>
      </div>

      <p v-if="error" class="settle__error" role="alert">{{ error }}</p>

      <TallyButton variant="secondary" full-width data-testid="settle-done" @click="emit('close')">{{
        t('common.done')
      }}</TallyButton>
    </div>
  </SheetPanel>
</template>

<style scoped>
.settle {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.settle__entry {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.settle__pending {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border: var(--border-card);
  border-radius: var(--radius-md);
  background: var(--lemon-tint);
}

.settle__pending-text {
  font-size: var(--text-caption);
  color: var(--ink-2);
  overflow-wrap: break-word;
  min-width: 0;
}

.settle__pay {
  /* A column, because the amount is a tappable box with a keypad that unfolds beneath it —
     an inline row once pushed its own button 92px past a 390px viewport. */
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.settle__error {
  color: var(--coral);
  font-size: var(--text-caption);
}
</style>
