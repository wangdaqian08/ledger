<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AmountKeypadField from '@/components/AmountKeypadField.vue'
import AmountText from '@/components/AmountText.vue'
import BalanceRow from '@/components/BalanceRow.vue'
import SheetPanel from '@/components/SheetPanel.vue'
import TallyButton from '@/components/TallyButton.vue'
import TextField from '@/components/TextField.vue'
import { api, type PaybackView, type SettlementRow } from '@/lib/api'

/**
 * Screen 7 — Settle up. One row per person; Pay files a trip-level settlement that stays
 * PENDING until the person owed (or the trip's creator) confirms it. That confirmation lives here
 * too: a settlement has no bill, so the item sheet never sees it — the recipient approves, rejects
 * or the claimant withdraws it right on this strip. "Done for now" closes the sheet, because
 * all-square is a derived state, never a button (§7a).
 */
const props = defineProps<{
  open: boolean
  tripId: string
  myMemberId: string
  youAreCreator: boolean
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
const rejecting = ref<string | null>(null)
const rejectReason = ref('')

watch(
  () => props.open,
  (open) => {
    if (!open) return
    paying.value = null
    error.value = ''
    reminded.value = null
    rejecting.value = null
    rejectReason.value = ''
  },
)

// Settlements between me and this row's person: ones still waiting on somebody, and ones already
// approved — the latter kept as a muted, undoable record rather than vanishing (§7a). Who may act on
// each is decided by the server (claim.viewerCanDecide / viewerCanUndo), never re-derived here.
const pendingOf = (row: SettlementRow) => row.pending.filter((p) => p.status === 'PENDING')
const settledOf = (row: SettlementRow) => row.settled

// A settlement you filed that they declined — the one place its reason reaches you, since a
// trip-level claim has no bill sheet. Shown only while nothing fresh is pending to this person
// (retrying speaks for itself), and only the newest, which carries the reason they gave.
const declinedOf = (row: SettlementRow): PaybackView[] => {
  if (pendingOf(row).length > 0 || row.rejected.length === 0) return []
  const latest = [...row.rejected]
    .sort((a, b) => (a.reviewedAt ?? '').localeCompare(b.reviewedAt ?? ''))
    .at(-1)
  return latest ? [latest] : []
}

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

async function undoClaim(claim: PaybackView) {
  // Undoing a *settled* payment re-opens the other person's balance, so it asks first; withdrawing
  // your own still-pending one moved nothing, so it does not.
  if (claim.status === 'APPROVED' && !confirm(t('settle.undoConfirm'))) return
  await act(() => api.undoPayback(claim.id))
}
const approveClaim = (paybackId: string) => act(() => api.approvePayback(paybackId))

async function rejectClaim(paybackId: string) {
  if (!rejectReason.value.trim()) return
  await act(() => api.rejectPayback(paybackId, rejectReason.value.trim()))
  if (!error.value) {
    rejecting.value = null
    rejectReason.value = ''
  }
}
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
          <div class="settle__pending-head">
            <span class="settle__pending-text">
              {{
                claim.fromMemberId === myMemberId
                  ? t('settle.sentForConfirmation', { name: row.displayName })
                  : t('settle.awaitingYou', { name: row.displayName })
              }}
            </span>
            <!-- U1: the amount that is actually waiting, shown — not just that something is. -->
            <AmountText
              :amount-minor="claim.amountMinor"
              size="sm"
              :currency-code="currencyCode"
              :symbol="symbol"
            />
          </div>

          <div class="settle__pending-actions">
            <!-- The claimant withdraws; the recipient (or creator) decides. A settlement's only home
                 is this strip, so both live here — each shown only where the server's flag allows. -->
            <TallyButton
              v-if="claim.fromMemberId === myMemberId"
              size="sm"
              variant="ghost"
              data-testid="pending-undo"
              @click="undoClaim(claim)"
            >
              {{ t('common.cancel') }}
            </TallyButton>
            <template v-if="claim.viewerCanDecide">
              <TallyButton
                size="sm"
                variant="secondary"
                data-testid="pending-reject"
                @click="rejecting = claim.id"
              >
                {{ t('settle.reject') }}
              </TallyButton>
              <TallyButton
                size="sm"
                variant="primary"
                data-testid="pending-approve"
                @click="approveClaim(claim.id)"
              >
                {{ t('settle.approve') }}
              </TallyButton>
            </template>
          </div>

          <form v-if="rejecting === claim.id" class="settle__reject" @submit.prevent="rejectClaim(claim.id)">
            <TextField
              v-model="rejectReason"
              test-id="pending-reject-reason"
              :placeholder="t('itemDetail.rejectReason')"
            />
            <TallyButton
              size="sm"
              variant="danger"
              data-testid="pending-reject-send"
              :disabled="!rejectReason.trim()"
              @click="rejectClaim(claim.id)"
            >
              {{ t('settle.reject') }}
            </TallyButton>
          </form>
        </div>

        <!-- Approved settlements: already reflected in the balance above, kept as a muted, undoable
             record so a mistaken confirmation is not a one-way door (§7a). -->
        <div
          v-for="claim in settledOf(row)"
          :key="claim.id"
          class="settle__settled"
          data-testid="settled-claim"
        >
          <div class="settle__pending-head">
            <span class="settle__settled-text">
              {{
                claim.fromMemberId === myMemberId
                  ? t('settle.youPaidThem', { name: row.displayName })
                  : t('settle.theyPaidYou', { name: row.displayName })
              }}
              · {{ t('settle.settled') }}
            </span>
            <AmountText
              :amount-minor="claim.amountMinor"
              size="sm"
              tone="settled"
              :currency-code="currencyCode"
              :symbol="symbol"
            />
          </div>
          <div v-if="claim.viewerCanUndo" class="settle__pending-actions">
            <TallyButton size="sm" variant="ghost" data-testid="settled-undo" @click="undoClaim(claim)">
              {{ t('common.undo') }}
            </TallyButton>
          </div>
        </div>

        <!-- A settlement they declined: the one surface its reason reaches the claimant, a trip-level
             claim having no bill sheet. The row still offers Pay, so a corrected one can be sent. -->
        <div
          v-for="claim in declinedOf(row)"
          :key="claim.id"
          class="settle__declined"
          data-testid="declined-claim"
        >
          <div class="settle__pending-head">
            <span class="settle__declined-text">{{
              t('settle.declinedByThem', { name: row.displayName })
            }}</span>
            <AmountText
              :amount-minor="claim.amountMinor"
              size="sm"
              tone="owe"
              :currency-code="currencyCode"
              :symbol="symbol"
            />
          </div>
          <p v-if="claim.rejectReason" class="settle__declined-reason">“{{ claim.rejectReason }}”</p>
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
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border: var(--border-card);
  border-radius: var(--radius-md);
  background: var(--lemon-tint);
}

.settle__pending-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.settle__pending-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}

.settle__pending-actions:empty {
  display: none;
}

.settle__reject {
  display: flex;
  gap: var(--space-2);
  align-items: center;
}

.settle__pending-text {
  font-size: var(--text-caption);
  color: var(--ink-2);
  overflow-wrap: break-word;
  min-width: 0;
}

/* A settled payment reads as done, not active: muted, sunk back, the way a settled expense row is. */
.settle__settled {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border: var(--border-card);
  border-radius: var(--radius-md);
  background: var(--paper-sunk);
  opacity: 0.72;
}

.settle__settled-text {
  font-size: var(--text-caption);
  color: var(--text-muted);
  overflow-wrap: break-word;
  min-width: 0;
}

/* A declined claim is a dead end that needs explaining, not a live action: sunk like a settled one,
   but its heading in coral to say the payment did not stick, with the reason quoted beneath. */
.settle__declined {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  padding: var(--space-2) var(--space-3);
  border: var(--border-card);
  border-radius: var(--radius-md);
  background: var(--paper-sunk);
}

.settle__declined-text {
  font-size: var(--text-caption);
  font-weight: var(--weight-semibold);
  color: var(--coral);
  overflow-wrap: break-word;
  min-width: 0;
}

.settle__declined-reason {
  font-size: var(--text-caption);
  color: var(--text-muted);
  overflow-wrap: anywhere;
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
