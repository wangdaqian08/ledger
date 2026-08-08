<script setup lang="ts">
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
    divider?: boolean
  }>(),
  { currencyCode: 'AUD', symbol: '$', pending: false, divider: true },
)

defineEmits<{ pay: []; remind: [] }>()
</script>

<template>
  <div class="row" :class="{ 'row--divided': divider }">
    <PersonAvatar :name="displayName" :hue="personHue" :size="40" />

    <div class="row__body">
      <div class="row__name">{{ displayName }}</div>
      <!-- The state is two short words and must stay on one line: "Owes / you" split across
           two reads like a different sentence. Names may wrap; the caption may not. -->
      <div class="row__state">
        <template v-if="pending">Waiting for confirmation</template>
        <template v-else-if="owedMinor === 0">All square</template>
        <template v-else-if="owedMinor > 0">Owes you</template>
        <template v-else>You owe</template>
      </div>
    </div>

    <AmountText
      :amount-minor="Math.abs(owedMinor)"
      :currency-code="currencyCode"
      :symbol="symbol"
      size="lg"
      :tone="owedMinor === 0 ? 'settled' : owedMinor > 0 ? 'owed' : 'owe'"
    />

    <TallyButton v-if="owedMinor > 0 && !pending" size="sm" variant="secondary" @click="$emit('remind')">
      Remind
    </TallyButton>
    <TallyButton v-else-if="owedMinor < 0 && !pending" size="sm" @click="$emit('pay')">Pay</TallyButton>
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
</style>
