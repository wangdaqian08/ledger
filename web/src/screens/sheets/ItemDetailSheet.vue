<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AmountText from '@/components/AmountText.vue'
import PersonAvatar from '@/components/PersonAvatar.vue'
import SettledBanner from '@/components/SettledBanner.vue'
import SheetPanel from '@/components/SheetPanel.vue'
import TallyBadge from '@/components/TallyBadge.vue'
import TallyButton from '@/components/TallyButton.vue'
import TextField from '@/components/TextField.vue'
import { api, type CategoryView, type ItemDetail, type TripView } from '@/lib/api'
import { currencySymbol } from '@/lib/money'

/**
 * Screen 5 — one bill, whole: the split, and the approval section.
 *
 * Paybacks are loaded when the sheet opens, not carried in the trip payload — they are unbounded
 * per item and this sheet is the only place that wants them (spec §6).
 */
const props = defineProps<{
  open: boolean
  itemId: string | null
  trip: TripView
  categories: CategoryView[]
}>()
const emit = defineEmits<{
  close: []
  changed: []
  payBack: [itemId: string, toName: string, prefillMinor: number]
  edit: [item: ItemDetail]
}>()

const { t } = useI18n()

const detail = ref<ItemDetail | null>(null)
const rejecting = ref<string | null>(null)
const rejectReason = ref('')
const error = ref('')
const busy = ref(false)

const symbol = computed(() => currencySymbol(props.trip.currencyCode))
const me = computed(() => props.trip.members.find((m) => m.isYou) ?? null)
const payerName = computed(() => memberName(detail.value?.payerMemberId ?? ''))
const iAmPayer = computed(() => detail.value?.payerMemberId === me.value?.id)

/** What I still owe on this bill: my share minus what I have already had approved. */
const myRemaining = computed(() => {
  const d = detail.value
  const my = me.value
  if (!d || !my) return 0
  const share = d.splits.find((s) => s.memberId === my.id)?.amountMinor ?? 0
  const repaid = d.paybacks
    .filter((p) => p.fromMemberId === my.id && p.status === 'APPROVED')
    .reduce((sum, p) => sum + p.amountMinor, 0)
  return Math.max(0, share - repaid)
})

watch(
  () => [props.open, props.itemId] as const,
  async ([open, itemId]) => {
    if (!open || !itemId) return
    detail.value = null
    error.value = ''
    rejecting.value = null
    detail.value = await api.itemDetail(itemId)
  },
)

const memberName = (memberId: string) => props.trip.members.find((m) => m.id === memberId)?.displayName ?? '?'

async function reload() {
  if (props.itemId) detail.value = await api.itemDetail(props.itemId)
  emit('changed')
}

async function act(action: () => Promise<unknown>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await action()
    await reload()
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : String(failure)
  } finally {
    busy.value = false
  }
}

const approve = (paybackId: string) => act(() => api.approvePayback(paybackId))
const undo = (paybackId: string) => act(() => api.undoPayback(paybackId))

async function reject(paybackId: string) {
  if (!rejectReason.value.trim()) return
  await act(() => api.rejectPayback(paybackId, rejectReason.value.trim()))
  rejecting.value = null
  rejectReason.value = ''
}

async function remove() {
  if (!props.itemId || !confirm(t('itemDetail.deleteConfirm'))) return
  await act(() => api.deleteItem(props.itemId!))
  emit('close')
}
</script>

<template>
  <SheetPanel :open="open" :title="detail?.title ?? ''" @close="emit('close')">
    <div v-if="detail" class="detail">
      <SettledBanner v-if="detail.state === 'ALL_SQUARE'" :message="t('common.settled')" />

      <div class="detail__totals">
        <div>
          <p class="detail__label">{{ t('itemDetail.total') }}</p>
          <AmountText
            :amount-minor="detail.amountMinor"
            size="lg"
            :currency-code="trip.currencyCode"
            :symbol="symbol"
          />
        </div>
        <div v-if="detail.yourShareMinor > 0 && !iAmPayer" class="detail__yours">
          <p class="detail__label">{{ t('itemDetail.yourShare') }}</p>
          <AmountText
            :amount-minor="detail.yourShareMinor"
            size="lg"
            tone="owe"
            :currency-code="trip.currencyCode"
            :symbol="symbol"
          />
        </div>
      </div>

      <section class="detail__section">
        <h3 class="detail__label">{{ t('itemDetail.paidBy') }}</h3>
        <div class="detail__row">
          <PersonAvatar
            :name="payerName"
            :hue="trip.members.find((m) => m.id === detail!.payerMemberId)?.personHue ?? 1"
            :size="36"
          />
          <span class="detail__name">{{ payerName }}</span>
          <span class="detail__hint">{{ t('itemDetail.frontedTheBill') }}</span>
          <AmountText
            :amount-minor="detail.amountMinor"
            size="sm"
            :currency-code="trip.currencyCode"
            :symbol="symbol"
          />
        </div>
      </section>

      <section class="detail__section">
        <!-- The count matters when the list is long: thirteen people is a scroll, and knowing the
             money went thirteen ways is the fact the heading owes you up front. -->
        <h3 class="detail__label">{{ t('itemDetail.howSplit', { count: detail.splits.length }) }}</h3>
        <div v-for="split in detail.splits" :key="split.memberId" class="detail__row">
          <PersonAvatar
            :name="memberName(split.memberId)"
            :hue="trip.members.find((m) => m.id === split.memberId)?.personHue ?? 1"
            :size="32"
          />
          <span class="detail__name">{{ memberName(split.memberId) }}</span>
          <span v-if="split.weight" class="detail__hint">×{{ split.weight }}</span>
          <AmountText
            :amount-minor="split.amountMinor"
            size="sm"
            :currency-code="trip.currencyCode"
            :symbol="symbol"
          />
        </div>
      </section>

      <section class="detail__section">
        <h3 class="detail__label">{{ t('itemDetail.paybacks') }}</h3>
        <p v-if="detail.paybacks.length === 0" class="detail__hint">{{ t('itemDetail.noPaybacks') }}</p>

        <div v-for="payback in detail.paybacks" :key="payback.id" class="detail__payback">
          <div class="detail__row">
            <PersonAvatar
              :name="memberName(payback.fromMemberId)"
              :hue="trip.members.find((m) => m.id === payback.fromMemberId)?.personHue ?? 1"
              :size="32"
            />
            <span class="detail__name">{{ memberName(payback.fromMemberId) }}</span>
            <TallyBadge
              :tone="
                payback.status === 'APPROVED' ? 'settled' : payback.status === 'PENDING' ? 'pending' : 'owe'
              "
            >
              {{
                payback.status === 'APPROVED'
                  ? t('payback.approved')
                  : payback.status === 'PENDING'
                    ? t('payback.pending', { name: payerName })
                    : t('payback.rejected')
              }}
            </TallyBadge>
            <AmountText
              :amount-minor="payback.amountMinor"
              size="sm"
              :currency-code="trip.currencyCode"
              :symbol="symbol"
            />
          </div>

          <p v-if="payback.rejectReason" class="detail__reason">{{ payback.rejectReason }}</p>

          <!-- The person owed decides; either side can undo before or after approval (§7a) — a
               settled bill can un-settle, the accepted cost of never trapping a wrong record.
               The server holds the real rule; these buttons only appear where they will succeed. -->
          <div v-if="payback.status !== 'REJECTED'" class="detail__decide">
            <template v-if="iAmPayer && payback.status === 'PENDING'">
              <TallyButton size="sm" variant="secondary" @click="rejecting = payback.id">
                {{ t('itemDetail.reject') }}
              </TallyButton>
              <TallyButton size="sm" variant="primary" @click="approve(payback.id)">
                {{ t('itemDetail.approve') }}
              </TallyButton>
            </template>
            <!-- The claimant can always withdraw; the person owed un-does an *approved* one
                 (a pending claim they disagree with has Reject, which says why). -->
            <TallyButton
              v-if="payback.fromMemberId === me?.id || (iAmPayer && payback.status === 'APPROVED')"
              size="sm"
              variant="ghost"
              @click="undo(payback.id)"
            >
              {{ t('common.cancel') }}
            </TallyButton>
          </div>

          <form v-if="rejecting === payback.id" class="detail__reject" @submit.prevent="reject(payback.id)">
            <TextField v-model="rejectReason" :placeholder="t('itemDetail.rejectReason')" />
            <TallyButton
              size="sm"
              variant="danger"
              :disabled="!rejectReason.trim()"
              @click="reject(payback.id)"
            >
              {{ t('itemDetail.reject') }}
            </TallyButton>
          </form>
        </div>
      </section>

      <p v-if="detail.note" class="detail__note">{{ detail.note }}</p>
      <p v-if="error" class="detail__error" role="alert">{{ error }}</p>

      <div class="detail__actions">
        <!-- The people list is the whole fix for the hotel case; the payer (the server also lets
             the creator) corrects it. EXACT bills stay out: their people change means retyping
             amounts, a different conversation. -->
        <TallyButton
          v-if="iAmPayer && detail.splitRule !== 'EXACT'"
          variant="secondary"
          size="sm"
          @click="emit('edit', detail)"
        >
          {{ t('editSplit.title') }}
        </TallyButton>
        <TallyButton v-if="iAmPayer" variant="danger" size="sm" @click="remove">
          {{ t('itemDetail.delete') }}
        </TallyButton>
        <!-- Never for the payer: their own share is not a debt, and the server refuses a
             self-payback anyway. -->
        <TallyButton
          v-if="myRemaining > 0 && !iAmPayer"
          variant="primary"
          full-width
          @click="emit('payBack', detail.id, payerName, myRemaining)"
        >
          {{ t('itemDetail.payBack') }}
        </TallyButton>
      </div>
    </div>
  </SheetPanel>
</template>

<style scoped>
.detail {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.detail__totals {
  display: flex;
  justify-content: space-between;
  gap: var(--space-4);
}

.detail__yours {
  text-align: right;
}

.detail__label {
  font-size: var(--text-label);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--ls-label);
  text-transform: uppercase;
  color: var(--text-muted);
  margin-bottom: var(--space-1);
}

.detail__section {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.detail__row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.detail__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: var(--weight-semibold);
  color: var(--text-body);
}

.detail__hint {
  color: var(--text-muted);
  font-size: var(--text-caption);
  white-space: nowrap;
}

.detail__payback {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.detail__reason {
  padding-left: calc(32px + var(--space-3));
  color: var(--coral);
  font-size: var(--text-caption);
  overflow-wrap: break-word;
}

.detail__decide {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}

.detail__reject {
  display: flex;
  gap: var(--space-2);
  align-items: center;
}

.detail__note {
  padding: var(--space-3);
  border: var(--border-card);
  border-radius: var(--radius-md);
  background: var(--paper-sunk);
  color: var(--ink-2);
  overflow-wrap: break-word;
}

.detail__error {
  color: var(--coral);
  font-size: var(--text-caption);
}

.detail__actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
</style>
