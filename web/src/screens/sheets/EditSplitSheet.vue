<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AmountKeypadField from '@/components/AmountKeypadField.vue'
import CategoryPicker from '@/components/CategoryPicker.vue'
import PersonToggleRow from '@/components/PersonToggleRow.vue'
import SheetPanel from '@/components/SheetPanel.vue'
import SplitBar, { type SplitPerson } from '@/components/SplitBar.vue'
import TallyButton from '@/components/TallyButton.vue'
import TextField from '@/components/TextField.vue'
import { api, type CategoryView, type ItemView, type TripView } from '@/lib/api'
import { todayLocal } from '@/lib/dates'
import { currencySymbol } from '@/lib/money'
import { saltFor, splitShares } from '@/lib/split'
import { DRAG_SCALE, normalizedWeights } from '@/lib/weights'

/**
 * Fixing a bill's people list — the mechanism the entire design rests on (spec §1): tick the
 * late arrivals on, and every share re-derives with nothing stored to go stale.
 *
 * The salt is the item's existing id, so the shares previewed here are exactly the shares every
 * screen shows after saving. EXACT-split items don't open this sheet: changing their people list
 * means retyping amounts, which is a different conversation.
 */
const props = defineProps<{
  open: boolean
  trip: TripView
  item: ItemView | null
  categories: CategoryView[]
}>()
const emit = defineEmits<{ close: []; saved: [] }>()

const { t, locale } = useI18n()

const amountMinor = ref(0)
const title = ref('')
const categoryId = ref<string | null>(null)
const spentOn = ref(todayLocal())
const payerId = ref<string | null>(null)
const ticked = ref<Record<string, boolean>>({})
const custom = ref(false)
const weights = ref<Record<string, number>>({})
const busy = ref(false)
const error = ref('')

const symbol = computed(() => currencySymbol(props.trip.currencyCode))

watch(
  () => [props.open, props.item] as const,
  ([open, item]) => {
    if (!open || !item) return
    error.value = ''
    amountMinor.value = item.amountMinor
    title.value = item.title
    categoryId.value = item.categoryId
    spentOn.value = item.spentOn
    payerId.value = item.payerMemberId
    custom.value = item.splitRule === 'WEIGHTED'
    ticked.value = Object.fromEntries(
      props.trip.members.map((m) => [m.id, item.splits.some((s) => s.memberId === m.id)]),
    )
    // Existing people keep their ratio, scaled up so the bar has room to move (see weights.ts);
    // anyone ticked on later starts at one scaled unit. Saving normalises back down.
    weights.value = Object.fromEntries(
      props.trip.members.map((m) => [
        m.id,
        (item.splits.find((s) => s.memberId === m.id)?.weight ?? 1) * DRAG_SCALE,
      ]),
    )
  },
)

/** Ticked people in roster order — the order sent, stored as positions, and previewed. */
const sharers = computed(() => props.trip.members.filter((m) => ticked.value[m.id]))

const splitPeople = computed<SplitPerson[]>(() =>
  sharers.value.map((m) => ({
    memberId: m.id,
    displayName: m.displayName,
    personHue: m.personHue,
    weight: weights.value[m.id] ?? 1,
  })),
)

const previewShares = computed<Map<string, number>>(() => {
  const item = props.item
  if (!item || sharers.value.length === 0) return new Map()
  const parts = splitShares({
    totalMinor: amountMinor.value,
    weights: sharers.value.map((m) => (custom.value ? (weights.value[m.id] ?? 1) : 1)),
    salt: saltFor(item.id),
  })
  return new Map(sharers.value.map((m, index) => [m.id, parts[index]!]))
})

function onWeights(next: SplitPerson[]) {
  for (const person of next) weights.value[person.memberId] = person.weight
}

async function save() {
  const item = props.item
  if (!item || busy.value || sharers.value.length === 0 || !payerId.value || amountMinor.value <= 0) return
  busy.value = true
  error.value = ''
  try {
    const saved = normalizedWeights(sharers.value.map((m) => weights.value[m.id] ?? DRAG_SCALE))
    await api.patchItem(item.id, {
      // Now the one edit surface for the whole bill, not just its split — a typo'd title or a
      // wrong day was otherwise only fixable by deleting the expense (spec §6 allows all three).
      title: title.value.trim() || item.title,
      categoryId: categoryId.value ?? item.categoryId,
      spentOn: spentOn.value,
      amountMinor: amountMinor.value,
      payerMemberId: payerId.value,
      splitRule: custom.value ? 'WEIGHTED' : 'EQUAL',
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
  <SheetPanel :open="open" :title="t('editSplit.title')" @close="emit('close')">
    <div v-if="item" class="edit">
      <section class="edit__section">
        <h3 class="edit__label">{{ t('addExpense.whatWasIt') }}</h3>
        <TextField v-model="title" test-id="edit-title" :placeholder="t('addExpense.titlePlaceholder')" />
      </section>

      <CategoryPicker v-model="categoryId" :categories="categories" :locale="locale === 'zh' ? 'zh' : 'en'" />

      <section class="edit__section">
        <h3 class="edit__label">{{ t('addExpense.when') }}</h3>
        <input v-model="spentOn" type="date" class="edit__date" data-testid="edit-date" :max="todayLocal()" />
      </section>

      <section class="edit__section">
        <h3 class="edit__label">{{ t('editSplit.amount') }}</h3>
        <AmountKeypadField
          v-model="amountMinor"
          test-id="edit-amount"
          :currency-code="trip.currencyCode"
          :symbol="symbol"
        />
      </section>

      <section class="edit__section">
        <h3 class="edit__label">{{ t('addExpense.whoPaid') }}</h3>
        <div class="edit__payers">
          <button
            v-for="member in trip.members"
            :key="member.id"
            type="button"
            class="edit__payer"
            data-testid="payer-chip"
            :class="{ 'edit__payer--on': payerId === member.id }"
            :aria-pressed="payerId === member.id"
            @click="payerId = member.id"
          >
            {{ member.isYou ? t('common.you') : member.displayName }}
          </button>
        </div>
      </section>

      <section class="edit__section">
        <h3 class="edit__label">{{ t('addExpense.splitBetween') }}</h3>
        <div class="edit__people">
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
      </section>

      <section class="edit__section">
        <div class="edit__how">
          <h3 class="edit__label">{{ t('addExpense.how') }}</h3>
          <div class="edit__toggle" role="group">
            <button
              type="button"
              class="edit__mode"
              :class="{ 'edit__mode--on': !custom }"
              :aria-pressed="!custom"
              @click="custom = false"
            >
              {{ t('addExpense.evenly') }}
            </button>
            <button
              type="button"
              class="edit__mode"
              :class="{ 'edit__mode--on': custom }"
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
          :salt="saltFor(item.id)"
          :currency-code="trip.currencyCode"
          :symbol="symbol"
          @update:people="onWeights"
        />
      </section>

      <p v-if="error" class="edit__error" role="alert">{{ error }}</p>

      <TallyButton
        variant="primary"
        full-width
        data-testid="save-split"
        :disabled="busy || sharers.length === 0 || amountMinor <= 0"
        @click="save"
      >
        {{ t('editSplit.save') }}
      </TallyButton>
    </div>
  </SheetPanel>
</template>

<style scoped>
.edit {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.edit__section {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.edit__label {
  font-size: var(--text-label);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--ls-label);
  text-transform: uppercase;
  color: var(--text-muted);
}

.edit__date {
  padding: var(--space-2) var(--space-3);
  border: 2px solid var(--hairline-strong);
  border-radius: var(--radius-md);
  background: var(--surface-card);
  font-family: var(--font-money);
  font-size: var(--text-body);
  color: var(--ink);
}

.edit__payers {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.edit__payer {
  padding: var(--space-2) var(--space-4);
  border: 2px solid var(--hairline-strong);
  border-radius: var(--radius-pill);
  background: var(--surface-card);
  font-weight: var(--weight-semibold);
  color: var(--ink-2);
  cursor: pointer;
  white-space: nowrap;
}

.edit__payer--on {
  border-color: var(--ink);
  background: var(--grape-tint);
  color: var(--ink);
}

.edit__people {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.edit__how {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.edit__toggle {
  display: flex;
  gap: var(--space-1);
}

.edit__mode {
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

.edit__mode--on {
  border-color: var(--ink);
  background: var(--grape-tint);
  color: var(--ink);
}

.edit__error {
  color: var(--coral);
  font-size: var(--text-caption);
}
</style>
