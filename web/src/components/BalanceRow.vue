<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import AmountText from './AmountText.vue'
import PersonAvatar from './PersonAvatar.vue'
import TallyButton from './TallyButton.vue'

/**
 * One Settle-up row: your position with one person, and the one action it affords.
 *
 * [owedMinor] is positive when they owe you and negative when you owe them — the viewer's own
 * frame. Note the API's settlement row states the same figure the other way round (positive =
 * you owe them), so a caller wiring one to the other negates it, once, at the call site.
 * Zero is *all square* on the nose — an integer comparison, not a tolerance. Tally compared
 * a float against 0.005; there is nothing to be tolerant of when the number is whole cents.
 *
 * The action follows the direction: you can only nudge somebody who owes you, and only pay
 * somebody you owe. Both are requests — Pay settles nothing until they confirm it (§7a).
 */
withDefaults(
  defineProps<{
    displayName: string
    personHue: number
    owedMinor: number
    currencyCode?: string
    symbol?: string
    /** A claim already sent between the two of you and waiting on somebody. */
    pending?: boolean
    /** A nudge already sent this sitting: the button itself says so, in place. */
    reminded?: boolean
    /** All-square rows are sunk to the bottom of Who-owes-who and faded — present, not prominent. */
    muted?: boolean
    divider?: boolean
  }>(),
  { currencyCode: 'AUD', symbol: '$', pending: false, reminded: false, muted: false, divider: true },
)

defineEmits<{ pay: []; remind: [] }>()

const { t } = useI18n()
</script>

<template>
  <div class="row" :class="{ 'row--divided': divider, 'row--muted': muted }" data-testid="balance-row">
    <PersonAvatar :name="displayName" :hue="personHue" :size="40" />

    <div class="row__body">
      <div class="row__name">{{ displayName }}</div>
      <!-- Direction stays put even while a claim is pending: which way the money goes is the one
           thing the row must never drop. The waiting note sits under it, not in place of it. -->
      <div class="row__state">
        <template v-if="owedMinor === 0">{{ t('money.allSquare') }}</template>
        <template v-else-if="owedMinor > 0">{{ t('settle.owesYouShort') }}</template>
        <template v-else>{{ t('settle.youOweShort') }}</template>
      </div>
      <div v-if="pending" class="row__pending">{{ t('settle.waiting') }}</div>
    </div>

    <AmountText
      :amount-minor="Math.abs(owedMinor)"
      :currency-code="currencyCode"
      :symbol="symbol"
      size="lg"
      :tone="owedMinor === 0 ? 'settled' : owedMinor > 0 ? 'owed' : 'owe'"
    />

    <TallyButton
      v-if="owedMinor > 0 && !pending"
      size="sm"
      variant="secondary"
      :disabled="reminded"
      data-testid="row-remind"
      @click="$emit('remind')"
    >
      {{ reminded ? t('settle.reminded') : t('settle.remind') }}
    </TallyButton>
    <TallyButton v-else-if="owedMinor < 0 && !pending" size="sm" data-testid="row-pay" @click="$emit('pay')">
      {{ t('settle.pay') }}
    </TallyButton>
  </div>
</template>

<style scoped>
.row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 12px var(--space-4);
  min-height: 64px;
}

.row--divided {
  border-bottom: 1.5px solid var(--hairline);
}

/* Settled with this person: the row stays for reassurance but reads as finished, sunk behind the
   debts that still need acting on. The same treatment a settled expense row and payment record get. */
.row--muted {
  opacity: 0.5;
}

.row__body {
  flex: 1;
  min-width: 0;
}

.row__name {
  font-weight: var(--weight-bold);
  color: var(--ink);
  line-height: 1.2;
  overflow-wrap: anywhere;
}

.row__state {
  font-size: var(--text-caption);
  color: var(--text-muted);
  margin-top: 2px;
  white-space: nowrap;
}

.row__pending {
  font-size: var(--text-caption);
  color: var(--lemon-ink, var(--text-subtle));
  margin-top: 1px;
  white-space: nowrap;
}
</style>
