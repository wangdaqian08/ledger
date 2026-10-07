<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AmountKeypadField from '@/components/AmountKeypadField.vue'
import CategoryPicker from '@/components/CategoryPicker.vue'
import SheetPanel from '@/components/SheetPanel.vue'
import SplitEditor from '@/components/SplitEditor.vue'
import TallyButton from '@/components/TallyButton.vue'
import TextField from '@/components/TextField.vue'
import { api, errorMessage, type CategoryView, type ItemView, type TripView } from '@/lib/api'
import { todayLocal } from '@/lib/dates'
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

const { t } = useI18n()

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
    error.value = errorMessage(failure)
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

      <CategoryPicker v-model="categoryId" :categories="categories" />

      <section class="edit__section">
        <h3 class="edit__label">{{ t('addExpense.when') }}</h3>
        <input v-model="spentOn" type="date" class="edit__date" data-testid="edit-date" :max="todayLocal()" />
      </section>

      <section class="edit__section">
        <h3 class="edit__label">{{ t('editSplit.amount') }}</h3>
        <AmountKeypadField v-model="amountMinor" test-id="edit-amount" :currency-code="trip.currencyCode" />
      </section>

      <SplitEditor
        v-model:payer-id="payerId"
        v-model:ticked="ticked"
        v-model:custom="custom"
        v-model:weights="weights"
        :members="trip.members"
        :item-id="item.id"
        :total-minor="amountMinor"
        :currency-code="trip.currencyCode"
      />

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

.edit__error {
  color: var(--coral);
  font-size: var(--text-caption);
}
</style>
