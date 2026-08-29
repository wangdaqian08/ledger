<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AmountText from '@/components/AmountText.vue'
import CommentField from '@/components/CommentField.vue'
import PersonAvatar from '@/components/PersonAvatar.vue'
import ReceiptLightbox from '@/components/ReceiptLightbox.vue'
import SettledBanner from '@/components/SettledBanner.vue'
import SheetPanel from '@/components/SheetPanel.vue'
import TallyBadge from '@/components/TallyBadge.vue'
import TallyButton from '@/components/TallyButton.vue'
import TallyIcon from '@/components/TallyIcon.vue'
import TextField from '@/components/TextField.vue'
import {
  api,
  receiptHref,
  type CategoryView,
  type ItemDetail,
  type PaybackView,
  type TripView,
} from '@/lib/api'
import { currencySymbol, formatMinor } from '@/lib/money'
import { prepareReceipt } from '@/lib/receipt'

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

const { t, locale } = useI18n()

const detail = ref<ItemDetail | null>(null)
const rejecting = ref<string | null>(null)
const rejectReason = ref('')
const error = ref('')
const busy = ref(false)
const lightboxOpen = ref(false)
const receiptInput = ref<HTMLInputElement | null>(null)
const editingNote = ref(false)
const noteDraft = ref('')
const noteTooLong = ref(false)

const symbol = computed(() => currencySymbol(props.trip.currencyCode))
const me = computed(() => props.trip.members.find((m) => m.isYou) ?? null)
const payerName = computed(() => memberName(detail.value?.payerMemberId ?? ''))
const iAmPayer = computed(() => detail.value?.payerMemberId === me.value?.id)
// The server lets the trip's creator correct or decide anything, not only the payer; the UI now
// offers what the server allows instead of making the creator discover it through a 403.
const iCanEdit = computed(() => iAmPayer.value || props.trip.youAreCreator)
// An ended trip's spending record is read-only (the server answers 409); viewing — receipts
// included — stays, because the retention window exists to be looked at while people settle.
const tripStillOpen = computed(() => !props.trip.closedAt)
const iCanEditReceipt = computed(() => iCanEdit.value && tripStillOpen.value)
// The comment is part of the spending record, so it is gated exactly like the receipt and the
// split: the payer or the creator, and only while the trip is open.
const iCanEditNote = computed(() => iCanEdit.value && tripStillOpen.value)
/**
 * Whether the box is showing rather than the paragraph — and the same value the field's own
 * disclosure is bound to, because Save and Discard are drawn here while the box is drawn there.
 * Two owners disagreed: folding the row hid the box and left those two buttons over a draft
 * nobody could see, above a comment that had vanished from the screen, under a label saying there
 * was none.
 *
 * So folding the row away *is* backing out of the edit, and unfolding it is starting one. True the
 * moment there is a draft either way, so words already typed can never be hidden.
 */
const noteEditorOpen = computed({
  get: () => editingNote.value || noteDraft.value !== '',
  set: (open) => (open ? editNote() : cancelNote()),
})

const categoryName = computed(() => {
  const category = props.categories.find((c) => c.id === detail.value?.categoryId)
  if (!category) return ''
  return locale.value.startsWith('zh') ? category.nameZh : category.nameEn
})
const spentOnLabel = computed(() => {
  const iso = detail.value?.spentOn
  if (!iso) return ''
  const [y, m, d] = iso.split('-').map(Number)
  return new Intl.DateTimeFormat(locale.value, { year: 'numeric', month: 'short', day: 'numeric' }).format(
    new Date(y!, m! - 1, d),
  )
})
const splitRuleLabel = computed(() =>
  detail.value?.splitRule === 'WEIGHTED'
    ? t('itemDetail.splitWeighted')
    : detail.value?.splitRule === 'EXACT'
      ? t('itemDetail.splitExact')
      : t('itemDetail.splitEqual'),
)

/**
 * What I still owe on this bill: my share minus what I have already claimed — pending claims
 * included, so filing one hides the button rather than inviting a duplicate.
 */
const myRemaining = computed(() => {
  const d = detail.value
  const my = me.value
  if (!d || !my) return 0
  const share = d.splits.find((s) => s.memberId === my.id)?.amountMinor ?? 0
  const repaid = d.paybacks
    .filter((p) => p.fromMemberId === my.id && (p.status === 'APPROVED' || p.status === 'PENDING'))
    .reduce((sum, p) => sum + p.amountMinor, 0)
  return Math.max(0, share - repaid)
})

// Each open bumps this; a fetch only writes its result if it is still the newest. Opening a
// different item before the first load returns must not let the slower response paint the wrong
// bill into a sheet the viewer could then approve or reject against.
let detailToken = 0
watch(
  () => [props.open, props.itemId] as const,
  async ([open, itemId]) => {
    if (!open || !itemId) return
    const token = ++detailToken
    detail.value = null
    error.value = ''
    rejecting.value = null
    lightboxOpen.value = false
    cancelNote()
    try {
      const loaded = await api.itemDetail(itemId)
      if (token === detailToken) detail.value = loaded
    } catch (failure) {
      // An unguarded fetch here left a blank, titleless sheet with no way to know why — a co-editor
      // may have just deleted the bill. Surface it instead of a silent empty panel.
      if (token === detailToken) error.value = failure instanceof Error ? failure.message : String(failure)
    }
  },
)

function pickReceipt() {
  receiptInput.value?.click()
}

async function onReceiptPicked(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !detail.value) return
  const itemId = detail.value.id
  await act(async () => {
    const prepared = await prepareReceipt(file)
    await api.uploadReceipt(itemId, prepared.image, prepared.filename)
  })
}

async function removeReceipt() {
  if (!detail.value || !confirm(t('receipt.removeConfirm'))) return
  const itemId = detail.value.id
  lightboxOpen.value = false
  await act(() => api.deleteReceipt(itemId))
}

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

function editNote() {
  noteDraft.value = detail.value?.note ?? ''
  editingNote.value = true
}

/** Backing out of the edit. Distinct from saving an emptied box, which deletes the comment. */
function cancelNote() {
  editingNote.value = false
  noteDraft.value = ''
  noteTooLong.value = false
}

async function saveNote() {
  if (!detail.value || busy.value || noteTooLong.value) return
  const itemId = detail.value.id
  const next = noteDraft.value.trim()
  // The server maps "" to null, so an emptied box is a deletion — and a deletion is asked about,
  // the same way removing a receipt is. Discard is the way out that changes nothing.
  if (next === '' && detail.value.note && !confirm(t('comment.removeConfirm'))) return

  await act(() => api.patchItem(itemId, { note: next }))
  // act() folds a failure into `error` and leaves the draft alone, so the words are still there to
  // try again with — only a landed patch closes the editor.
  if (!error.value) cancelNote()
}

async function undo(payback: PaybackView) {
  // Undoing a *confirmed* repayment re-opens a balance the other person thought was closed, so it
  // asks first. Withdrawing your own still-pending claim moved nothing, so it does not.
  if (payback.status === 'APPROVED' && !confirm(t('itemDetail.undoConfirm'))) return
  await act(() => api.undoPayback(payback.id))
}

/** Show undo where the server allows it: the claimant withdrawing, or the owed/creator un-settling. */
const canUndo = (payback: PaybackView) =>
  payback.viewerCanUndo && (payback.fromMemberId === me.value?.id || payback.status === 'APPROVED')

async function reject(paybackId: string) {
  if (!rejectReason.value.trim()) return
  await act(() => api.rejectPayback(paybackId, rejectReason.value.trim()))
  // Only clear the form when the reject actually landed. act() swallows a failure into `error`, so
  // resetting unconditionally would throw away a reason the person now has to retype — the same
  // guard SettleUpSheet.rejectClaim already uses.
  if (!error.value) {
    rejecting.value = null
    rejectReason.value = ''
  }
}

async function remove() {
  if (!props.itemId || busy.value || !confirm(t('itemDetail.deleteConfirm'))) return

  // Confirmed repayments are records of money that really changed hands, and they die with the
  // bill (spec §5: item claims cascade). That is never a single-tap decision: name the count and
  // the sum, and ask again.
  const approved = (detail.value?.paybacks ?? []).filter((p) => p.status === 'APPROVED')
  if (approved.length > 0) {
    const total = approved.reduce((sum, p) => sum + p.amountMinor, 0)
    const amount = formatMinor(total, {
      currencyCode: props.trip.currencyCode,
      symbol: symbol.value,
    })
    if (!confirm(t('itemDetail.deleteApprovedConfirm', { count: approved.length, amount }))) return
  }

  // Not routed through act(): that reloads the item, and this item is about to not exist. Refresh
  // the feed (so the deleted row and the moved balances land) and close — but only once the delete
  // has actually succeeded. A refused delete (a 403) keeps the sheet open with the reason, rather
  // than closing and destroying the message.
  busy.value = true
  error.value = ''
  try {
    await api.deleteItem(props.itemId)
    emit('changed')
    emit('close')
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : String(failure)
  } finally {
    busy.value = false
  }
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
        <div v-if="detail.yourShareMinor > 0" class="detail__yours">
          <p class="detail__label">{{ t('itemDetail.yourPortion') }}</p>
          <!-- The payer sees their share too — it is not a debt (they fronted the bill), so it is
               shown in neutral ink rather than the owe colour. -->
          <AmountText
            :amount-minor="detail.yourShareMinor"
            size="lg"
            :tone="iAmPayer ? 'neutral' : 'owe'"
            :currency-code="trip.currencyCode"
            :symbol="symbol"
          />
        </div>
      </div>

      <!-- Category, date and how it was split — the facts the detail sheet used to omit. -->
      <p v-if="categoryName || spentOnLabel" class="detail__meta">
        {{ [categoryName, spentOnLabel, splitRuleLabel].filter(Boolean).join(' · ') }}
      </p>

      <section v-if="detail.receipt || iCanEditReceipt" class="detail__section">
        <h3 class="detail__label">{{ t('receipt.section') }}</h3>
        <!-- Tapping the thumbnail opens the actual bill full screen for review. -->
        <button
          v-if="detail.receipt"
          type="button"
          class="detail__thumb-button"
          data-testid="receipt-thumb"
          @click="lightboxOpen = true"
        >
          <img
            class="detail__thumb"
            data-testid="receipt-image"
            :src="receiptHref(detail.id, detail.receipt.version)"
            :alt="t('receipt.alt')"
          />
        </button>
        <TallyButton
          v-else
          variant="secondary"
          size="sm"
          data-testid="receipt-add"
          :disabled="busy"
          @click="pickReceipt"
        >
          <TallyIcon name="camera" :size="16" />
          {{ t('receipt.add') }}
        </TallyButton>
        <input
          ref="receiptInput"
          class="detail__file"
          type="file"
          accept="image/*"
          data-testid="receipt-input"
          @change="onReceiptPicked"
        />
      </section>

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
        <h3 class="detail__label" data-testid="how-split">
          {{ t('itemDetail.howSplit', { count: detail.splits.length }) }} · {{ splitRuleLabel }}
        </h3>
        <div v-for="split in detail.splits" :key="split.memberId" class="detail__row" data-testid="split-row">
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

        <div
          v-for="payback in detail.paybacks"
          :key="payback.id"
          class="detail__payback"
          data-testid="payback-row"
        >
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
                    ? iAmPayer
                      ? t('itemDetail.waitingOn')
                      : t('payback.pending', { name: payerName })
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
            <!-- The server decides who may act; these buttons only render where its flag is set, so
                 none of them can lead to a 403. -->
            <template v-if="payback.viewerCanDecide">
              <TallyButton
                size="sm"
                variant="secondary"
                data-testid="reject-open"
                @click="rejecting = payback.id"
              >
                {{ t('itemDetail.reject') }}
              </TallyButton>
              <TallyButton size="sm" variant="primary" data-testid="approve" @click="approve(payback.id)">
                {{ t('itemDetail.approve') }}
              </TallyButton>
            </template>
            <!-- The claimant withdraws a pending claim; the person owed or the creator un-does an
                 approved one (which un-settles the bill, so it asks first). -->
            <TallyButton
              v-if="canUndo(payback)"
              size="sm"
              variant="ghost"
              data-testid="undo-claim"
              @click="undo(payback)"
            >
              {{ payback.status === 'APPROVED' ? t('common.undo') : t('common.cancel') }}
            </TallyButton>
          </div>

          <form v-if="rejecting === payback.id" class="detail__reject" @submit.prevent="reject(payback.id)">
            <TextField
              v-model="rejectReason"
              test-id="reject-reason"
              :placeholder="t('itemDetail.rejectReason')"
            />
            <TallyButton
              size="sm"
              variant="danger"
              data-testid="reject-send"
              :disabled="!rejectReason.trim()"
              @click="reject(payback.id)"
            >
              {{ t('itemDetail.reject') }}
            </TallyButton>
          </form>
        </div>
      </section>

      <!-- Nothing at all when there is no comment and no right to write one. -->
      <section v-if="detail.note || iCanEditNote" class="detail__section">
        <!-- Read-only for anyone who cannot correct the bill, an ended trip included. -->
        <p v-if="!iCanEditNote" class="detail__note">{{ detail.note }}</p>
        <!-- A comment that exists is its own way in: tap the words to change them. Named for what
             it does, though, not for what it says — the comment's own text is all a screen reader
             would otherwise read out, followed by "button" and no clue why. -->

        <button
          v-else-if="detail.note && !noteEditorOpen"
          type="button"
          class="detail__note detail__note--tap"
          data-testid="comment-edit"
          :aria-label="t('comment.edit')"
          @click="editNote"
        >
          {{ detail.note }}
        </button>
        <template v-else>
          <CommentField v-model="noteDraft" v-model:open="noteEditorOpen" v-model:invalid="noteTooLong" />
          <div v-if="noteEditorOpen" class="detail__note-actions">
            <TallyButton size="sm" variant="ghost" data-testid="comment-discard" @click="cancelNote">
              {{ t('comment.discard') }}
            </TallyButton>
            <TallyButton
              size="sm"
              variant="primary"
              data-testid="comment-save"
              :disabled="busy || noteTooLong"
              @click="saveNote"
            >
              {{ t('comment.save') }}
            </TallyButton>
          </div>
        </template>
      </section>

      <p v-if="error" class="detail__error" role="alert">{{ error }}</p>

      <div class="detail__actions">
        <!-- The people list is the whole fix for the hotel case; the payer (the server also lets
             the creator) corrects it. EXACT bills stay out: their people change means retyping
             amounts, a different conversation. An ended trip hides both — the server would 409. -->
        <TallyButton
          v-if="iCanEdit && tripStillOpen && detail.splitRule !== 'EXACT'"
          variant="secondary"
          size="sm"
          data-testid="edit-split-open"
          @click="emit('edit', detail)"
        >
          {{ t('editSplit.title') }}
        </TallyButton>
        <TallyButton
          v-if="iCanEdit && tripStillOpen"
          variant="danger"
          size="sm"
          data-testid="delete-item"
          @click="remove"
        >
          {{ t('itemDetail.delete') }}
        </TallyButton>
        <!-- Never for the payer: their own share is not a debt, and the server refuses a
             self-payback anyway. -->
        <TallyButton
          v-if="myRemaining > 0 && !iAmPayer"
          variant="primary"
          full-width
          data-testid="pay-back-open"
          @click="emit('payBack', detail.id, payerName, myRemaining)"
        >
          {{ t('itemDetail.payBack') }}
        </TallyButton>
      </div>

      <ReceiptLightbox
        v-if="detail.receipt"
        :open="lightboxOpen"
        :src="receiptHref(detail.id, detail.receipt.version)"
        :can-edit="iCanEditReceipt"
        :busy="busy"
        @close="lightboxOpen = false"
        @replace="pickReceipt"
        @remove="removeReceipt"
      />
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

.detail__meta {
  font-size: var(--text-caption);
  color: var(--text-muted);
  overflow-wrap: break-word;
}

.detail__thumb-button {
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  width: fit-content;
}

.detail__thumb {
  display: block;
  width: 96px;
  height: 96px;
  object-fit: cover;
  border: 2px solid var(--ink);
  border-radius: var(--radius-md);
  box-shadow: var(--slab-1);
}

/* In the DOM for the picker dialog, out of the layout. */
.detail__file {
  display: none;
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

/* The paragraph doubling as an edit button: the note's own look, with a button's reset. */
.detail__note--tap {
  width: 100%;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.detail__note-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
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
