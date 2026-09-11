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
      <div class="row__name" :title="displayName" data-testid="balance-row-name">{{ displayName }}</div>
      <!-- Direction stays put even while a claim is pending: which way the money goes is the one
           thing the row must never drop. The waiting note sits under it, not in place of it. -->
      <div class="row__state">
        <template v-if="owedMinor === 0">{{ t('money.allSquare') }}</template>
        <template v-else-if="owedMinor > 0">{{ t('settle.owesYouShort') }}</template>
        <template v-else>{{ t('settle.youOweShort') }}</template>
      </div>
      <div v-if="pending" class="row__pending">{{ t('settle.waiting') }}</div>
    </div>

    <!-- Amount and its one action ride together: on a narrow row this whole group wraps to a second
         line rather than crushing the name (see .row__trailing). -->
    <div class="row__trailing">
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
      <TallyButton
        v-else-if="owedMinor < 0 && !pending"
        size="sm"
        data-testid="row-pay"
        @click="$emit('pay')"
      >
        {{ t('settle.pay') }}
      </TallyButton>
    </div>
  </div>
</template>

<style scoped>
.row {
  display: flex;
  /* Wrap rather than crush: when the name can't keep its width beside the amount and action, the
     trailing group drops to a second line instead of the name clipping away to nothing. No width
     breakpoint — the row reflows on its own content, the way the rest of this app's layouts do. */
  flex-wrap: wrap;
  align-items: center;
  column-gap: var(--space-3);
  row-gap: var(--space-1);
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
  flex: 1 1 auto;
  /* Content-aware minimum: the body never shrinks below the name's own width, so the name never
     clips — and a row whose name fits stays on one line. Only when the name genuinely can't sit
     beside the amount + action does the trailing group wrap below it (flex-wrap on .row), rather
     than a fixed breakpoint that would stack every row or none. */
  min-width: min-content;
}

.row__name {
  font-weight: var(--weight-bold);
  color: var(--ink);
  line-height: 1.2;
  /* One line, clipped with an ellipsis rather than broken mid-word ("Stephi/ne") when a long name
     meets a big amount — the same treatment ExpenseRow.row__title gets. The avatar carries the
     initial and the title attribute carries the full name, so a clipped name is never a lost one. */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Both sub-lines clip with the name, never overflow it. Without overflow:hidden a squeezed body
   collapses to zero width and "Owes you" spills its nowrap text out over the amount beside it. */
.row__state {
  font-size: var(--text-caption);
  color: var(--text-muted);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row__pending {
  font-size: var(--text-caption);
  /* --text-subtle, not the never-defined --lemon-ink whose fallback this always was: the note has
     always rendered in this grey. If the "waiting" line should read amber to match the lemon-tint
     pending theme, that wants a real --lemon-ink token in colors.css, not a phantom reference here. */
  color: var(--text-subtle);
  margin-top: 1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* The amount and its action travel as one block that never shrinks: the money is never clipped (the
   house rule) and the action stays tappable. margin-left:auto pins the block to the right — on a
   wide row after the name, and on its own line once wrapped. So the name column is the only thing
   that yields, and when it runs out of room the block drops below rather than squeezing it away. */
.row__trailing {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-left: auto;
  flex-shrink: 0;
}
</style>
