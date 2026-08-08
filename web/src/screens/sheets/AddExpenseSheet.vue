<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import CategoryPicker from '@/components/CategoryPicker.vue'
import PersonToggleRow from '@/components/PersonToggleRow.vue'
import SheetPanel from '@/components/SheetPanel.vue'
import SplitBar, { type SplitPerson } from '@/components/SplitBar.vue'
import TallyButton from '@/components/TallyButton.vue'
import TallyKeypad, { type KeypadKey } from '@/components/TallyKeypad.vue'
import TextField from '@/components/TextField.vue'
import { api, type CategoryView, type TripView } from '@/lib/api'
import { currencySymbol, formatMinor } from '@/lib/money'
import { newItemId, saltFor, splitShares } from '@/lib/split'
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
const categoryId = ref<string | null>(null)
const payerId = ref<string | null>(null)
const ticked = ref<Record<string, boolean>>({})
const custom = ref(false)
const weights = ref<Record<string, number>>({})
const busy = ref(false)
const error = ref('')

const symbol = computed(() => currencySymbol(props.trip.currencyCode))

watch(
  () => props.open,
  (open) => {
    if (!open) return
    // A fresh sheet is a fresh expense: new id, everyone ticked, even split, you paid.
    step.value = 1
    itemId.value = newItemId()
    amountMinor.value = 0
    title.value = ''
    error.value = ''
    custom.value = false
    categoryId.value = props.categories.find((c) => c.key === 'food')?.id ?? props.categories[0]?.id ?? null
    payerId.value = props.trip.members.find((m) => m.isYou)?.id ?? props.trip.members[0]?.id ?? null
    ticked.value = Object.fromEntries(props.trip.members.map((m) => [m.id, true]))
    // Scaled up so the bar has room to move (see weights.ts); saved weights normalise back down.
    weights.value = Object.fromEntries(props.trip.members.map((m) => [m.id, DRAG_SCALE]))
  },
)

function onKey(key: KeypadKey) {
  if (key === 'del') {
    amountMinor.value = Math.floor(amountMinor.value / 10)
    return
  }
  const shifted = amountMinor.value * 10 ** key.length + Number(key)
  // Refusing beats wrapping: past the safe range the digits would stop being exact.
  if (shifted <= Number.MAX_SAFE_INTEGER) amountMinor.value = shifted
}

const shown = computed(() =>
  formatMinor(amountMinor.value, { currencyCode: props.trip.currencyCode, symbol: '' }),
)

/** The people on the bill, in roster order — the order the server will store as positions. */
const sharers = computed(() => props.trip.members.filter((m) => ticked.value[m.id]))

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
  busy.value = true
  error.value = ''
  try {
    // A bar dragged to 40:20 means 2:1 — the drag scale divides back out before saving.
    const saved = normalizedWeights(sharers.value.map((m) => weights.value[m.id] ?? DRAG_SCALE))
    await api.createItem(props.trip.id, {
      id: itemId.value,
      title: title.value.trim() || t('addExpense.titlePlaceholder'),
      categoryId: categoryId.value,
      amountMinor: amountMinor.value,
      splitRule: custom.value ? 'WEIGHTED' : 'EQUAL',
      payerMemberId: payerId.value,
      spentOn: new Date().toISOString().slice(0, 10),
      sharedBy: sharers.value.map((m, index) =>
        custom.value ? { memberId: m.id, weight: saved[index] } : { memberId: m.id },
      ),
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
  <SheetPanel
    :open="open"
    :title="step === 1 ? t('addExpense.howMuch') : t('addExpense.whoSplitIt')"
    @close="emit('close')"
  >
    <div v-if="step === 1" class="add">
      <p class="add__amount">
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
        :label="t('addExpense.whatWasIt')"
        :placeholder="t('addExpense.titlePlaceholder')"
      />

      <TallyKeypad @key="onKey" />

      <TallyButton variant="primary" full-width :disabled="amountMinor === 0" @click="step = 2">
        {{ t('addExpense.next') }}
      </TallyButton>
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
            :class="{ 'add__payer--on': payerId === member.id }"
            :aria-pressed="payerId === member.id"
            @click="payerId = member.id"
          >
            {{ member.isYou ? 'You' : member.displayName }}
          </button>
        </div>
      </section>

      <section class="add__section">
        <h3 class="add__label">{{ t('addExpense.splitBetween') }}</h3>
        <div class="add__people">
          <PersonToggleRow
            v-for="member in trip.members"
            :key="member.id"
            :display-name="member.isYou ? 'You' : member.displayName"
            :person-hue="member.personHue"
            :selected="ticked[member.id] ?? false"
            :share-minor="previewShares.get(member.id) ?? null"
            :currency-code="trip.currencyCode"
            :symbol="symbol"
            @update:selected="(on) => (ticked[member.id] = on)"
          />
        </div>
        <p v-if="!custom && evenEach && amountMinor > 0" class="add__each">
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
              :class="{ 'add__mode--on': !custom }"
              :aria-pressed="!custom"
              @click="custom = false"
            >
              {{ t('addExpense.evenly') }}
            </button>
            <button
              type="button"
              class="add__mode"
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

      <p v-if="error" class="add__error" role="alert">{{ error }}</p>

      <div class="add__actions">
        <TallyButton variant="secondary" @click="step = 1">{{ t('addExpense.back') }}</TallyButton>
        <TallyButton variant="primary" :disabled="busy || sharers.length === 0" @click="save">
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
  padding: var(--space-4) 0;
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

.add__error {
  color: var(--coral);
  font-size: var(--text-caption);
}

.add__actions {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: var(--space-3);
}
</style>
