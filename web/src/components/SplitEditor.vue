<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import PersonToggleRow from './PersonToggleRow.vue'
import SplitBar, { type SplitPerson } from './SplitBar.vue'
import type { MemberView } from '@/lib/api'
import { saltFor, splitShares } from '@/lib/split'

/**
 * Who paid, who shares, and how — the half of a bill the add and edit sheets have in common.
 *
 * The salt is the item's id, so the shares previewed against each name are the shares the server
 * will derive. Rendered as a fragment: its three sections sit straight in the sheet's own column.
 * The `all` slot lands beside the "Split between" heading, the `each` slot under the people list.
 */
const props = defineProps<{
  members: MemberView[]
  itemId: string
  totalMinor: number
  currencyCode: string
}>()
const payerId = defineModel<string | null>('payerId', { required: true })
const ticked = defineModel<Record<string, boolean>>('ticked', { required: true })
const custom = defineModel<boolean>('custom', { required: true })
const weights = defineModel<Record<string, number>>('weights', { required: true })

const { t } = useI18n()

/** Ticked people in roster order — the order sent, stored as positions, and previewed. */
const sharers = computed(() => props.members.filter((m) => ticked.value[m.id]))

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
  if (sharers.value.length === 0) return new Map()
  const parts = splitShares({
    totalMinor: props.totalMinor,
    weights: sharers.value.map((m) => (custom.value ? (weights.value[m.id] ?? 1) : 1)),
    salt: saltFor(props.itemId),
  })
  return new Map(sharers.value.map((m, index) => [m.id, parts[index]!]))
})

const modes = computed(() => [
  { custom: false, testId: 'mode-evenly', label: t('addExpense.evenly') },
  { custom: true, testId: 'mode-custom', label: t('addExpense.custom') },
])

function onWeights(next: SplitPerson[]) {
  weights.value = { ...weights.value, ...Object.fromEntries(next.map((p) => [p.memberId, p.weight])) }
}
</script>

<template>
  <section class="editor__section">
    <h3 class="editor__label">{{ t('addExpense.whoPaid') }}</h3>
    <div class="editor__payers">
      <button
        v-for="member in members"
        :key="member.id"
        type="button"
        class="editor__payer"
        data-testid="payer-chip"
        :class="{ 'editor__payer--on': payerId === member.id }"
        :aria-pressed="payerId === member.id"
        @click="payerId = member.id"
      >
        {{ member.isYou ? t('common.you') : member.displayName }}
      </button>
    </div>
  </section>

  <section class="editor__section">
    <div v-if="$slots.all" class="editor__how">
      <h3 class="editor__label">{{ t('addExpense.splitBetween') }}</h3>
      <slot name="all" />
    </div>
    <h3 v-else class="editor__label">{{ t('addExpense.splitBetween') }}</h3>
    <div class="editor__people">
      <PersonToggleRow
        v-for="member in members"
        :key="member.id"
        :display-name="member.isYou ? t('common.you') : member.displayName"
        :person-hue="member.personHue"
        :selected="ticked[member.id] ?? false"
        :share-minor="previewShares.get(member.id) ?? null"
        :currency-code="currencyCode"
        @update:selected="(on) => (ticked = { ...ticked, [member.id]: on })"
      />
    </div>
    <slot name="each" />
  </section>

  <section class="editor__section">
    <div class="editor__how">
      <h3 class="editor__label">{{ t('addExpense.how') }}</h3>
      <div class="editor__toggle" role="group">
        <button
          v-for="mode in modes"
          :key="mode.testId"
          type="button"
          class="editor__mode"
          :data-testid="mode.testId"
          :class="{ 'editor__mode--on': custom === mode.custom }"
          :aria-pressed="custom === mode.custom"
          @click="custom = mode.custom"
        >
          {{ mode.label }}
        </button>
      </div>
    </div>
    <SplitBar
      v-if="custom && splitPeople.length > 1"
      :people="splitPeople"
      :total-minor="totalMinor"
      :salt="saltFor(itemId)"
      :currency-code="currencyCode"
      @update:people="onWeights"
    />
  </section>
</template>

<style scoped>
.editor__section {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.editor__label {
  font-size: var(--text-label);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--ls-label);
  text-transform: uppercase;
  color: var(--text-muted);
}

.editor__payers {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.editor__payer {
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

.editor__payer--on {
  border-color: var(--ink);
  background: var(--grape-tint);
  color: var(--ink);
}

.editor__people {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.editor__how {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.editor__toggle {
  display: flex;
  gap: var(--space-1);
}

.editor__mode {
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

.editor__mode--on {
  border-color: var(--ink);
  background: var(--grape-tint);
  color: var(--ink);
}
</style>
