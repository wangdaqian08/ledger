<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import AmountText from './AmountText.vue'
import TallyIcon from './TallyIcon.vue'

/** Tally's canonical glyph and hue per built-in category (spec §5). No emoji, ever. */
const CATEGORY_LOOK: Record<string, { icon: string; hue: number }> = {
  food: { icon: 'utensils', hue: 2 },
  drinks: { icon: 'beer', hue: 3 },
  transport: { icon: 'car-front', hue: 5 },
  stay: { icon: 'bed-double', hue: 7 },
  groceries: { icon: 'shopping-basket', hue: 4 },
  fun: { icon: 'ticket', hue: 8 },
  home: { icon: 'house', hue: 6 },
  other: { icon: 'circle-dashed', hue: 1 },
}

/**
 * One expense in a trip's feed: category disc, title, who paid, and your stake in the bill.
 *
 * The trailing figure is that stake, not a live debt: your *share* of the bill when somebody else
 * paid, or what you *fronted* for everyone else when you did. It is fixed by the split and does not
 * move when you settle up — repaying your $125 share does not change your $125 share of the bill.
 * Whether you still owe anything is a different question, answered (netting repayments) by the hero
 * and the Who-owes-who rows. Wording this figure "you owe" made a paid-up person read it as an open
 * debt, so it stays neutral here and red/green is kept for those live-balance surfaces.
 *
 * `allSquare` comes from the server's derived item state, not from comparing a balance to zero
 * here. It is square when every sharer's *approved* paybacks cover their portion, which is a
 * different question from whether your own share happens to be nil.
 */
const props = withDefaults(
  defineProps<{
    title: string
    categoryKey?: string
    /** The payer's display name; when [paidByYou] is set it is not shown, "You paid" is. */
    paidBy?: string
    paidByYou?: boolean
    spentOn?: string
    yourShareMinor: number
    allSquare?: boolean
    currencyCode?: string
    symbol?: string
    divider?: boolean
  }>(),
  {
    categoryKey: 'other',
    paidBy: undefined,
    paidByYou: false,
    spentOn: undefined,
    allSquare: false,
    currencyCode: 'AUD',
    symbol: '$',
    divider: true,
  },
)

defineEmits<{ click: [] }>()

const { t } = useI18n()

const look = () => CATEGORY_LOOK[props.categoryKey] ?? CATEGORY_LOOK.other!
const paidLabel = () =>
  props.paidByYou ? t('trip.youPaid') : props.paidBy ? t('trip.paidBy', { name: props.paidBy }) : null
const subtitle = () => [paidLabel(), props.spentOn].filter(Boolean).join(' · ')
</script>

<template>
  <button
    type="button"
    class="row"
    :class="{ 'row--divided': divider, 'row--settled': allSquare }"
    data-testid="expense-row"
    @click="$emit('click')"
  >
    <span
      class="row__disc"
      :style="{
        background: `var(--person-${look().hue})`,
        color: look().hue === 3 ? 'var(--ink)' : 'var(--text-on-accent)',
      }"
    >
      <TallyIcon :name="look().icon" :size="22" />
    </span>

    <span class="row__body">
      <span class="row__title">{{ title }}</span>
      <span class="row__subtitle">{{ subtitle() }}</span>
    </span>

    <span class="row__trailing">
      <AmountText
        :amount-minor="Math.abs(yourShareMinor)"
        :currency-code="currencyCode"
        :symbol="symbol"
        :tone="allSquare ? 'settled' : 'neutral'"
        :show-sign="false"
      />
      <span class="row__caption">
        {{
          allSquare ? t('trip.settledCaption') : paidByYou ? t('trip.frontedCaption') : t('trip.shareCaption')
        }}
      </span>
    </span>
  </button>
</template>

<style scoped>
.row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  padding: 12px var(--space-4);
  min-height: 64px;
  background: transparent;
  border: none;
  cursor: pointer;
  text-align: left;
}

.row--divided {
  border-bottom: 1.5px solid var(--hairline);
}

/* Settled expenses read as finished, not active: the disc and text fade back so an open bill is
   what the eye lands on. Reopen it (undo a repayment) and it returns to full strength. */
.row--settled {
  opacity: 0.62;
}

.row--settled .row__disc {
  filter: grayscale(0.5);
}

.row:hover {
  background: var(--surface-card-hover);
}

.row__disc {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  flex: 0 0 auto;
  border: 2px solid var(--ink);
  border-radius: var(--radius-md);
}

.row__body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.row__title {
  font-family: var(--font-core);
  font-weight: var(--weight-bold);
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row__subtitle {
  font-size: var(--text-caption);
  color: var(--text-muted);
  margin-top: 2px;
}

.row__trailing {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.row__caption {
  font-size: 11px;
  font-weight: var(--weight-bold);
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--text-subtle);
}
</style>
