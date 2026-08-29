<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import CategoryPicker from '@/components/CategoryPicker.vue'
import PersonToggleRow from '@/components/PersonToggleRow.vue'
import ReceiptLightbox from '@/components/ReceiptLightbox.vue'
import SheetPanel from '@/components/SheetPanel.vue'
import SplitBar, { type SplitPerson } from '@/components/SplitBar.vue'
import TallyButton from '@/components/TallyButton.vue'
import TallyIcon from '@/components/TallyIcon.vue'
import CommentField from '@/components/CommentField.vue'
import TallyKeypad, { type KeypadKey } from '@/components/TallyKeypad.vue'
import TextField from '@/components/TextField.vue'
import { api, type CategoryView, type TripView } from '@/lib/api'
import { todayLocal } from '@/lib/dates'
import { currencySymbol, formatMinor } from '@/lib/money'
import { prepareReceipt } from '@/lib/receipt'
import { newItemId, saltFor, splitShares } from '@/lib/split'
import { pressKey } from '@/lib/till'
import { DRAG_SCALE, normalizedWeights } from '@/lib/weights'

/**
 * Screen 4 — the two-step add sheet: how much, then who.
 *
 * The item's id is minted the moment the sheet opens, because the id is the split's salt: the
 * amounts previewed against each name — spare cents included — are the amounts the server will
 * derive, and a retry of the save cannot double the expense.
 */
const props = defineProps<{ open: boolean; trip: TripView; categories: CategoryView[] }>()
const emit = defineEmits<{ close: []; saved: [] }>()

const { t, locale } = useI18n()

const step = ref<1 | 2>(1)
const itemId = ref(newItemId())
const amountMinor = ref(0)
const title = ref('')
const spentOn = ref(todayLocal())
const categoryId = ref<string | null>(null)
const payerId = ref<string | null>(null)
const ticked = ref<Record<string, boolean>>({})
const custom = ref(false)
const weights = ref<Record<string, number>>({})
const busy = ref(false)
const note = ref('')
const noteOpen = ref(false)
const noteTooLong = ref(false)
const error = ref('')
const receiptFile = ref<File | null>(null)
const receiptUrl = ref('')
const receiptInput = ref<HTMLInputElement | null>(null)
const reviewOpen = ref(false)

const symbol = computed(() => currencySymbol(props.trip.currencyCode))

watch(
  () => props.open,
  (open) => {
    if (!open) return
    // A fresh sheet is a fresh expense: new id, nobody ticked, even split, you paid, dated today.
    // Nobody ticked (spec §3) makes inclusion a deliberate act — the All chip is one tap when it is
    // everyone, and a late joiner is never silently charged for a bill from before they arrived.
    step.value = 1
    itemId.value = newItemId()
    amountMinor.value = 0
    title.value = ''
    spentOn.value = todayLocal()
    note.value = ''
    noteOpen.value = false
    noteTooLong.value = false
    error.value = ''
    custom.value = false
    categoryId.value = props.categories.find((c) => c.key === 'food')?.id ?? props.categories[0]?.id ?? null
    payerId.value = props.trip.members.find((m) => m.isYou)?.id ?? props.trip.members[0]?.id ?? null
    ticked.value = Object.fromEntries(props.trip.members.map((m) => [m.id, false]))
    // Scaled up so the bar has room to move (see weights.ts); saved weights normalise back down.
    weights.value = Object.fromEntries(props.trip.members.map((m) => [m.id, DRAG_SCALE]))
    clearReceipt()
  },
)

function onReceiptPicked(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (receiptUrl.value) URL.revokeObjectURL(receiptUrl.value)
  receiptFile.value = file
  // The preview shows the original straight away; the downscale happens once, at save time.
  receiptUrl.value = URL.createObjectURL(file)
}

function clearReceipt() {
  if (receiptUrl.value) URL.revokeObjectURL(receiptUrl.value)
  receiptFile.value = null
  receiptUrl.value = ''
  reviewOpen.value = false
  if (receiptInput.value) receiptInput.value.value = ''
}

function onKey(key: KeypadKey) {
  amountMinor.value = pressKey(amountMinor.value, key)
}

const shown = computed(() =>
  formatMinor(amountMinor.value, { currencyCode: props.trip.currencyCode, symbol: '' }),
)

/** The people on the bill, in roster order — the order the server will store as positions. */
const sharers = computed(() => props.trip.members.filter((m) => ticked.value[m.id]))

/** A blank title falls back to the category's name in the reader's language, never the example. */
function fallbackTitle(): string {
  const category = props.categories.find((c) => c.id === categoryId.value)
  if (category) return locale.value.startsWith('zh') ? category.nameZh : category.nameEn
  return t('addExpense.untitled')
}

/** The All chip: on only when everyone shares; one tap selects or clears the whole roster. */
const allTicked = computed(() => props.trip.members.every((m) => ticked.value[m.id]))
function toggleAll() {
  const on = !allTicked.value
  ticked.value = Object.fromEntries(props.trip.members.map((m) => [m.id, on]))
}

/** Whether closing now would throw away real work — used to confirm an accidental dismissal. */
const isDirty = computed(
  () =>
    step.value === 2 ||
    amountMinor.value > 0 ||
    title.value.trim() !== '' ||
    note.value.trim() !== '' ||
    receiptFile.value !== null,
)

const splitPeople = computed<SplitPerson[]>(() =>
  sharers.value.map((m) => ({
    memberId: m.id,
    displayName: m.displayName,
    personHue: m.personHue,
    weight: weights.value[m.id] ?? 1,
  })),
)

/** The engine's own answer, before saving: what each ticked person will actually be charged. */
const previewShares = computed<Map<string, number>>(() => {
  if (sharers.value.length === 0 || amountMinor.value === 0) return new Map()
  const parts = splitShares({
    totalMinor: amountMinor.value,
    weights: sharers.value.map((m) => (custom.value ? (weights.value[m.id] ?? 1) : 1)),
    salt: saltFor(itemId.value),
  })
  return new Map(sharers.value.map((m, index) => [m.id, parts[index]!]))
})

/**
 * "$25.00 each" — shown only when the division is exact. When it is not, "each" would be a lie
 * by a cent for somebody, and the per-person rows above already show the exact answer.
 */
const evenEach = computed(() => {
  const count = sharers.value.length
  if (count === 0 || amountMinor.value % count !== 0) return null
  return formatMinor(amountMinor.value / count, {
    currencyCode: props.trip.currencyCode,
    symbol: symbol.value,
  })
})

function onWeights(next: SplitPerson[]) {
  for (const person of next) weights.value[person.memberId] = person.weight
}

async function save() {
  if (busy.value || sharers.value.length === 0 || !payerId.value || !categoryId.value) return
  if (noteTooLong.value) return
  busy.value = true
  error.value = ''
  try {
    // A bar dragged to 40:20 means 2:1 — the drag scale divides back out before saving.
    const saved = normalizedWeights(sharers.value.map((m) => weights.value[m.id] ?? DRAG_SCALE))
    await api.createItem(props.trip.id, {
      id: itemId.value,
      title: title.value.trim() || fallbackTitle(),
      categoryId: categoryId.value,
      amountMinor: amountMinor.value,
      splitRule: custom.value ? 'WEIGHTED' : 'EQUAL',
      payerMemberId: payerId.value,
      spentOn: spentOn.value,
      note: note.value.trim() || undefined,
      sharedBy: sharers.value.map((m, index) =>
        custom.value ? { memberId: m.id, weight: saved[index] } : { memberId: m.id },
      ),
    })
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : String(failure)
    busy.value = false
    return
  }
  // The expense is in. The photo is a separate step against the same minted id, so a failure here
  // does not undo it — say exactly that, keep the sheet open, and let a second Save retry just the
  // photo (the create replays as a 200). The old code showed one generic error that read as "not
  // saved", inviting a re-entry that doubled the bill.
  if (receiptFile.value) {
    try {
      const prepared = await prepareReceipt(receiptFile.value)
      await api.uploadReceipt(itemId.value, prepared.image, prepared.filename)
    } catch (failure) {
      const reason = failure instanceof Error ? failure.message : String(failure)
      error.value = t('addExpense.savedPhotoFailed', { reason })
      busy.value = false
      return
    }
  }
  busy.value = false
  emit('saved')
}
</script>

<template>
  <SheetPanel
    :open="open"
    :title="step === 1 ? t('addExpense.howMuch') : t('addExpense.whoSplitIt')"
    :confirm-close="isDirty ? t('addExpense.discardConfirm') : undefined"
    @close="emit('close')"
  >
    <div v-if="step === 1" class="add">
      <p class="add__amount" data-testid="amount-display">
        <span class="add__symbol">{{ symbol }}</span>
        <span class="add__digits">{{ amountMinor === 0 ? '0' : shown }}</span>
      </p>

      <CategoryPicker
        v-model="categoryId"
        :categories="categories.map((c) => ({ ...c, hue: c.hue }))"
        :locale="locale === 'zh' ? 'zh' : 'en'"
      />

      <TextField
        v-model="title"
        test-id="expense-title"
        :label="t('addExpense.whatWasIt')"
        :placeholder="t('addExpense.titlePlaceholder')"
      />

      <label class="add__field">
        <span class="add__field-label">{{ t('addExpense.when') }}</span>
        <input
          v-model="spentOn"
          type="date"
          class="add__date"
          data-testid="expense-date"
          :max="todayLocal()"
        />
      </label>

      <TallyKeypad @key="onKey" />

      <div class="add__footer">
        <TallyButton
          variant="primary"
          full-width
          data-testid="next-step"
          :disabled="amountMinor === 0"
          @click="step = 2"
        >
          {{ t('addExpense.next') }}
        </TallyButton>
      </div>
    </div>

    <div v-else class="add">
      <section class="add__section">
        <h3 class="add__label">{{ t('addExpense.whoPaid') }}</h3>
        <div class="add__payers">
          <button
            v-for="member in trip.members"
            :key="member.id"
            type="button"
            class="add__payer"
            data-testid="payer-chip"
            :class="{ 'add__payer--on': payerId === member.id }"
            :aria-pressed="payerId === member.id"
            @click="payerId = member.id"
          >
            {{ member.isYou ? t('common.you') : member.displayName }}
          </button>
        </div>
      </section>

      <section class="add__section">
        <div class="add__how">
          <h3 class="add__label">{{ t('addExpense.splitBetween') }}</h3>
          <!-- Nobody is ticked by default (spec §3); this is the one-tap "it was everyone" case. -->
          <button
            type="button"
            class="add__all"
            data-testid="split-all"
            :class="{ 'add__all--on': allTicked }"
            :aria-pressed="allTicked"
            @click="toggleAll"
          >
            {{ t('addExpense.all') }}
          </button>
        </div>
        <div class="add__people">
          <PersonToggleRow
            v-for="member in trip.members"
            :key="member.id"
            :display-name="member.isYou ? t('common.you') : member.displayName"
            :person-hue="member.personHue"
            :selected="ticked[member.id] ?? false"
            :share-minor="previewShares.get(member.id) ?? null"
            :currency-code="trip.currencyCode"
            :symbol="symbol"
            @update:selected="(on) => (ticked[member.id] = on)"
          />
        </div>
        <p v-if="!custom && evenEach && amountMinor > 0" class="add__each" data-testid="even-each">
          {{ t('addExpense.each', { amount: evenEach }) }}
        </p>
      </section>

      <section class="add__section">
        <div class="add__how">
          <h3 class="add__label">{{ t('addExpense.how') }}</h3>
          <div class="add__toggle" role="group">
            <button
              type="button"
              class="add__mode"
              data-testid="mode-evenly"
              :class="{ 'add__mode--on': !custom }"
              :aria-pressed="!custom"
              @click="custom = false"
            >
              {{ t('addExpense.evenly') }}
            </button>
            <button
              type="button"
              class="add__mode"
              data-testid="mode-custom"
              :class="{ 'add__mode--on': custom }"
              :aria-pressed="custom"
              @click="custom = true"
            >
              {{ t('addExpense.custom') }}
            </button>
          </div>
        </div>
        <SplitBar
          v-if="custom && splitPeople.length > 1"
          :people="splitPeople"
          :total-minor="amountMinor"
          :salt="saltFor(itemId)"
          :currency-code="trip.currencyCode"
          :symbol="symbol"
          @update:people="onWeights"
        />
      </section>

      <section class="add__section">
        <h3 class="add__label">{{ t('receipt.section') }}</h3>
        <div v-if="receiptUrl" class="add__receipt">
          <!-- Tapping the preview opens it full screen — the photo gets reviewed before it is
               saved, not discovered blurry a week later. -->
          <button
            type="button"
            class="add__thumb-button"
            data-testid="receipt-preview"
            @click="reviewOpen = true"
          >
            <img class="add__thumb" :src="receiptUrl" :alt="t('receipt.alt')" />
          </button>
          <TallyButton variant="ghost" size="sm" data-testid="receipt-clear" @click="clearReceipt">
            {{ t('receipt.remove') }}
          </TallyButton>
        </div>
        <TallyButton
          v-else
          variant="secondary"
          size="sm"
          data-testid="receipt-add"
          @click="receiptInput?.click()"
        >
          <TallyIcon name="camera" :size="16" />
          {{ t('receipt.add') }}
        </TallyButton>
        <input
          ref="receiptInput"
          class="add__file"
          type="file"
          accept="image/*"
          data-testid="receipt-input"
          @change="onReceiptPicked"
        />
      </section>

      <section class="add__section">
        <CommentField v-model="note" v-model:open="noteOpen" v-model:invalid="noteTooLong" />
      </section>
      <p v-if="error" class="add__error" role="alert">{{ error }}</p>

      <ReceiptLightbox :open="reviewOpen" :src="receiptUrl" :can-edit="false" @close="reviewOpen = false" />

      <div class="add__actions add__footer">
        <TallyButton variant="secondary" data-testid="back-step" @click="step = 1">{{
          t('addExpense.back')
        }}</TallyButton>
        <TallyButton
          variant="primary"
          data-testid="save-expense"
          :disabled="busy || sharers.length === 0 || noteTooLong"
          @click="save"
        >
          {{ t('addExpense.save') }}
        </TallyButton>
      </div>
    </div>
  </SheetPanel>
</template>

<style scoped>
.add {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.add__amount {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  /* Pinned while the rest scrolls: the number being typed is the whole point of the screen,
     and a till with the display on the back is no till. Negative margins let the wash span the
     sheet's full width across the body's padding. */
  position: sticky;
  top: calc(-1 * var(--space-4));
  z-index: 2;
  margin: calc(-1 * var(--space-4)) calc(-1 * var(--space-4)) 0;
  background: var(--bg-app);
  border-bottom: 1.5px solid var(--hairline);
}

.add__symbol {
  font-family: var(--font-money);
  font-size: var(--text-money-lg);
  font-weight: var(--weight-bold);
  color: var(--text-muted);
}

.add__digits {
  font-family: var(--font-money);
  font-variant-numeric: tabular-nums;
  font-size: var(--text-money-hero);
  font-weight: var(--weight-bold);
  color: var(--ink);
  /* An amount is never clipped: it wraps as a whole and shrinks nowhere. */
  white-space: nowrap;
}

.add__section {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.add__label {
  font-size: var(--text-label);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--ls-label);
  text-transform: uppercase;
  color: var(--text-muted);
}

.add__payers {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.add__payer {
  padding: var(--space-2) var(--space-4);
  border: 2px solid var(--hairline-strong);
  border-radius: var(--radius-pill);
  background: var(--surface-card);
  font-weight: var(--weight-semibold);
  color: var(--ink-2);
  cursor: pointer;
  /* Names wrap the chip row, never truncate inside a chip. */
  white-space: nowrap;
}

.add__payer--on {
  border-color: var(--ink);
  background: var(--grape-tint);
  color: var(--ink);
}

.add__people {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.add__each {
  font-size: var(--text-caption);
  color: var(--text-muted);
}

.add__field {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.add__field-label {
  font-size: var(--text-label);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--ls-label);
  text-transform: uppercase;
  color: var(--text-muted);
}

.add__date {
  padding: var(--space-2) var(--space-3);
  border: 2px solid var(--hairline-strong);
  border-radius: var(--radius-md);
  background: var(--surface-card);
  font-family: var(--font-money);
  font-size: var(--text-body);
  color: var(--ink);
}

.add__all {
  padding: var(--space-1) var(--space-3);
  border: 2px solid var(--hairline-strong);
  border-radius: var(--radius-pill);
  background: var(--surface-card);
  font-size: var(--text-caption);
  font-weight: var(--weight-semibold);
  color: var(--ink-2);
  cursor: pointer;
  white-space: nowrap;
}

.add__all--on {
  border-color: var(--ink);
  background: var(--grape-tint);
  color: var(--ink);
}

.add__how {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.add__toggle {
  display: flex;
  gap: var(--space-1);
}

.add__mode {
  padding: var(--space-1) var(--space-3);
  border: 2px solid var(--hairline-strong);
  border-radius: var(--radius-pill);
  background: var(--surface-card);
  font-size: var(--text-caption);
  font-weight: var(--weight-semibold);
  color: var(--ink-2);
  cursor: pointer;
  white-space: nowrap;
}

.add__mode--on {
  border-color: var(--ink);
  background: var(--grape-tint);
  color: var(--ink);
}

.add__receipt {
  display: flex;
  align-items: flex-end;
  gap: var(--space-2);
}

.add__thumb-button {
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  width: fit-content;
}

.add__thumb {
  display: block;
  width: 96px;
  height: 96px;
  object-fit: cover;
  border: 2px solid var(--ink);
  border-radius: var(--radius-md);
  box-shadow: var(--slab-1);
}

/* In the DOM for the picker dialog, out of the layout — the camera button is its whole face. */
.add__file {
  display: none;
}

.add__error {
  color: var(--coral);
  font-size: var(--text-caption);
}

.add__actions {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: var(--space-3);
}

/* The way onward never needs scrolling for: pinned to the sheet's bottom edge, over the body's
   padding, with its own background so content slides beneath it. */
.add__footer {
  position: sticky;
  bottom: calc(-1 * var(--space-4));
  z-index: 2;
  margin: 0 calc(-1 * var(--space-4)) calc(-1 * var(--space-4));
  padding: var(--space-3) var(--space-4);
  background: var(--bg-app);
  border-top: 1.5px solid var(--hairline);
}
</style>
