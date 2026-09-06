<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PersonToggleRow from './PersonToggleRow.vue'
import TallyButton from './TallyButton.vue'
import type { MemberView } from '@/lib/api'

/**
 * The Family builder sub-view on Settle-up (§7b): ticks a subset of `candidates` and commits it as
 * one new Family. `candidates` is expected to already be *only* the trip's currently-unassigned
 * members — SettleUpSheet excludes anyone already in a built Family before this ever mounts, so
 * exclusion is structural, never a rejected submission.
 *
 * `mustLeaveOneOut` is true only when nothing has been built yet this session: ticking literally
 * every candidate then would make this the trip's only Family, which the server refuses (§7b needs
 * 2+). Blocked here, with a reason shown, rather than let the viewer submit it and hit a server
 * error for a rule they had no way to see coming.
 *
 * No live financial preview while ticking (unlike AddExpenseSheet's "$X each"): a Family-vs-Family
 * figure needs `owesBetween` sums the client does not have, so amounts only appear in the committed
 * partition view, after a commit re-fetches it.
 *
 * `ticked` is local state seeded once from `initialTicked`, not a live two-way model: SettleUpSheet
 * shows a rejected build's error message right alongside this component, and for reasons not fully
 * pinned down in Vue's reconciliation of the surrounding conditional siblings, that alone was enough
 * to tear down and recreate this instance — which would otherwise wipe `ticked` back to empty right
 * as the rejection is shown. A continuously-synced model would survive that too, but at the cost of
 * echoing every tick straight back up to the parent and — empirically — remounting on every one of
 * them as well, which a plain local ref never did. Ticking here stays purely local, exactly as
 * before; only `commit()`'s payload, which the caller already needs, doubles as the seed a fresh
 * instance restores from, so the parent only has reason to touch it once per commit attempt.
 */
const props = defineProps<{
  candidates: MemberView[]
  mustLeaveOneOut: boolean
  /** What the caller's *last commit attempt* ticked, if any — restored here on mount so a rejected
   *  attempt's selection survives whatever remounted this, without this component ever needing to
   *  report every tick as it happens. Empty for an ordinary fresh open. */
  initialTicked?: Record<string, boolean>
}>()
const emit = defineEmits<{ built: [memberIds: string[]]; cancel: [] }>()

const { t } = useI18n()

const ticked = ref<Record<string, boolean>>({ ...props.initialTicked })
const selectedCount = computed(() => Object.values(ticked.value).filter(Boolean).length)
const hasSelection = computed(() => selectedCount.value > 0)
const coversEveryone = computed(
  () =>
    props.mustLeaveOneOut && props.candidates.length > 0 && selectedCount.value === props.candidates.length,
)
const canCommit = computed(() => hasSelection.value && !coversEveryone.value)

function commit() {
  if (!canCommit.value) return
  // Candidate order, not tick order — deterministic regardless of the order tapped, the same way
  // AddExpenseSheet's `sharers` is the roster filtered by `ticked`, never the tick sequence itself.
  emit(
    'built',
    props.candidates.filter((m) => ticked.value[m.id]).map((m) => m.id),
  )
}
</script>

<template>
  <div class="builder">
    <h3 class="builder__label">{{ t('settle.chooseFamily') }}</h3>
    <div class="builder__people">
      <PersonToggleRow
        v-for="member in candidates"
        :key="member.id"
        :display-name="member.isYou ? t('common.you') : member.displayName"
        :person-hue="member.personHue"
        :selected="ticked[member.id] ?? false"
        @update:selected="(on) => (ticked[member.id] = on)"
      />
    </div>
    <p v-if="coversEveryone" class="builder__hint" data-testid="family-builder-hint">
      {{ t('settle.familyCannotBeEveryone') }}
    </p>
    <div class="builder__actions">
      <TallyButton variant="secondary" data-testid="family-builder-cancel" @click="emit('cancel')">
        {{ t('common.cancel') }}
      </TallyButton>
      <TallyButton variant="primary" data-testid="family-builder-add" :disabled="!canCommit" @click="commit">
        {{ t('settle.addFamily') }}
      </TallyButton>
    </div>
  </div>
</template>

<style scoped>
.builder {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.builder__label {
  font-size: var(--text-label);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--ls-label);
  text-transform: uppercase;
  color: var(--text-muted);
}

.builder__people {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.builder__hint {
  font-size: var(--text-caption);
  color: var(--coral);
}

.builder__actions {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: var(--space-3);
}
</style>
