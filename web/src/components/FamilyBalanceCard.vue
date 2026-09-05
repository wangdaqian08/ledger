<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import AmountText from './AmountText.vue'
import AvatarStack from './AvatarStack.vue'
import FamilyCounterpartRow from './FamilyCounterpartRow.vue'
import TallyButton from './TallyButton.vue'
import TallyCard from './TallyCard.vue'
import type {FamilyCounterpartView, FamilyMemberView} from '@/lib/api'

/**
 * One Family's card in the Settle-up partition view (§7b): its own net across the whole trip, and
 * one bilateral row per *other* Family in the same partition — never per individual, and never the
 * engine's separate minimised-transfer set.
 *
 * `removable` says whether this card came from an explicitly-built Family (carries Undo) or is an
 * automatic singleton (does not) — decided by the caller, which alone knows what it built.
 */
const props = withDefaults(
  defineProps<{
    members: FamilyMemberView[]
    netMinor: number
    counterparts: FamilyCounterpartView[]
    removable: boolean
    currencyCode?: string
    symbol?: string
  }>(),
  { currencyCode: 'AUD', symbol: '$' },
)
defineEmits<{ remove: [] }>()

const { t, locale } = useI18n()

const joinedNames = computed(() =>
  new Intl.ListFormat(locale.value, { type: 'conjunction' }).format(props.members.map((m) => m.displayName)),
)
const netLable = computed(() => {
  if (props.netMinor === 0) return t('money.allSquare')
  return props.netMinor > 0 ? t('settle.familyNetOwed'):t('settle.familyNetOwes')
})
</script>

<template>
  <TallyCard class="family" data-testid="family-card">
    <div class="family__head">
      <AvatarStack :people="members" :size="32" />
      <div class="family__name">{{ joinedNames }}</div>
      <TallyButton
        v-if="removable"
        variant="ghost"
        size="sm"
        data-testid="family-undo"
        @click="$emit('remove')"
      >
        {{ t('common.undo') }}
      </TallyButton>
    </div>

    <!-- The family's own net across the whole trip — same convention as TripView.yourNetMinor
         (positive = owed to them), so no sign flip belongs here, unlike the counterpart rows below. -->
    <div class="family__net-label">{{netLable}}</div>
    <AmountText
      :amount-minor="Math.abs(netMinor)"
      size="lg"
      :tone="netMinor === 0 ? 'settled' : netMinor > 0 ? 'owed' : 'owe'"
      :currency-code="currencyCode"
      :symbol="symbol"
    />

    <div class="family__counterparts">
      <FamilyCounterpartRow
        v-for="counterpart in counterparts"
        :key="counterpart.members.map((m) => m.id).join(',')"
        :members="counterpart.members"
        :owed-minor="-counterpart.owedMinor"
        :card-name="joinedNames"
        :card-member-count="members.length"
        :currency-code="currencyCode"
        :symbol="symbol"
      />
    </div>
  </TallyCard>
</template>

<style scoped>
.family {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.family__head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.family__name {
  flex: 1;
  min-width: 0;
  font-size: var(--text-heading-sm);
  font-weight: var(--weight-bold);
  color: var(--ink);
  overflow-wrap: anywhere;
}
.family__net-label {
  font-size: var(--text-caption);
  color: var(--text-muted);
}

.family__counterparts {
  display: flex;
  flex-direction: column;
}

.family__counterparts > :not(:last-child) {
  border-bottom: 1.5px solid var(--hairline);
}
</style>
