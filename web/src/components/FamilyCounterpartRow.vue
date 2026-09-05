<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import AmountText from './AmountText.vue'
import AvatarStack from './AvatarStack.vue'
import type {FamilyMemberView} from '@/lib/api'

/**
 * One Family's bilateral position with one *other* Family in the partition (§7b) — never an
 * individual. `BalanceRow` cannot represent this: it expects one name/hue, and a Family has no
 * single person who could unambiguously tap Pay or Remind on its behalf, so this offers neither.
 *
 * Same sign convention as `BalanceRow`: positive when the counterpart shown on *this* row owes the
 * enclosing Family, negative when the enclosing Family owes them. The wire figure
 * (`FamilyCounterpartView.owedMinor`) is stated the other way round — positive means the *enclosing*
 * Family owes the counterpart, matching `SettlementRow.owedMinor` — so the caller (FamilyBalanceCard)
 * flips it once, exactly the way `SettleUpSheet`/`TripScreen` flip `SettlementRow.owedMinor` before
 * handing it to `BalanceRow`. Zero is all-square on the nose, never a tolerance.
 */
const props = withDefaults(
  defineProps<{
    members: FamilyMemberView[]
    owedMinor: number
    currencyCode?: string
    symbol?: string
  }>(),
  { currencyCode: 'AUD', symbol: '$' },
)

const { t, locale } = useI18n()

const joinedNames = computed(() =>
  new Intl.ListFormat(locale.value, { type: 'conjunction' }).format(props.members.map((m) => m.displayName)),
)
</script>

<template>
  <div class="counterpart" data-testid="family-counterpart-row">
    <AvatarStack :people="members" :size="28" />
    <div class="counterpart__body">
      <div class="counterpart__name">{{ joinedNames }}</div>
      <div class="counterpart__state">
        <template v-if="owedMinor === 0">{{ t('money.allSquare') }}</template>
        <template v-else-if="owedMinor > 0">{{ t('settle.familyOwesShort') }}</template>
        <template v-else>{{ t('settle.familyOwedShort') }}</template>
      </div>
    </div>
    <AmountText
      :amount-minor="Math.abs(owedMinor)"
      :currency-code="currencyCode"
      :symbol="symbol"
      size="sm"
      :tone="owedMinor === 0 ? 'settled' : owedMinor > 0 ? 'owed' : 'owe'"
    />
  </div>
</template>

<style scoped>
.counterpart {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) 0;
}

.counterpart__body {
  flex: 1;
  min-width: 0;
}

.counterpart__name {
  font-weight: var(--weight-bold);
  color: var(--ink);
  line-height: 1.2;
  overflow-wrap: anywhere;
}

.counterpart__state {
  font-size: var(--text-caption);
  color: var(--text-muted);
  margin-top: 2px;
}
</style>
