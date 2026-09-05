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
 * Names both sides on the row itself — "Cara and Dana owe Alice and Bob" — rather than a bare
 * "Owes"/"Owed" caption sitting next to just the counterpart's name. That short form read as a
 * statement about the counterpart alone, with no way to tell which of the two families it was
 * relative to; a full sentence needs no anchor and reads the same regardless of which of the two
 * cards it's shown on.
 *
 * The whole sentence is one translated unit, not assembled from a spliced-in verb: English puts
 * whichever side owes first ("X owes Y"), but Chinese's own phrasing (待收/待付) always keeps this
 * card first and isn't a transitive verb at all, so the two languages don't share a common word
 * order to assemble from parts — each owns four complete sentences (two directions × singular/
 * plural, picked by whichever side is doing the owing in that sentence, which can be a one-person
 * Family on either side). `cardName`/`cardMemberCount` describe the *enclosing* `FamilyBalanceCard`,
 * supplied by it directly rather than re-joined here from its own members a second time.
 *
 * The verb itself renders bold. Getting there without `v-html` (the names on either side of it are
 * user-typed display names, so raw HTML injection is a real risk, not a theoretical one) means the
 * component re-splits its own already-translated sentence on the exact verb text, rather than
 * asking the translation for three pieces to begin with — the whole sentence stays one reviewable,
 * translatable unit, and the split is just a rendering detail.
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
    /** This row's enclosing FamilyBalanceCard — already joined, so this doesn't redo that work. */
    cardName: string
    cardMemberCount: number
    owedMinor: number
    currencyCode?: string
    symbol?: string
  }>(),
  { currencyCode: 'AUD', symbol: '$' },
)

const { t, locale } = useI18n()

const theirName = computed(() =>
  new Intl.ListFormat(locale.value, { type: 'conjunction' }).format(props.members.map((m) => m.displayName)),
)

// Sentence and verb keys are chosen together — same direction, same count — so the verb text
// below is guaranteed to be an exact substring of the sentence it's about to be split out of.
const keys = computed(() => {
  const owesCard = props.owedMinor > 0
  const count = owesCard ? props.members.length : props.cardMemberCount
  const suffix = count === 1 ? 'Singular' : 'Plural'
  return owesCard
    ? { sentence: `settle.familyOwesCard${suffix}`, verb: `settle.familyOwesCardVerb${suffix}` }
    : { sentence: `settle.familyCardOwes${suffix}`, verb: `settle.familyCardOwesVerb${suffix}` }
})

/** The verb rendered bold, split out of the sentence rather than asked for separately — see the
 *  component doc comment above for why this avoids `v-html`. Falls back to the plain sentence,
 *  unbolded, if the verb text somehow isn't found in it (it always should be; this is a seatbelt,
 *  not an expected path). */
const sentenceParts = computed(() => {
  const sentence = t(keys.value.sentence, { other: theirName.value, card: props.cardName })
  const verb = t(keys.value.verb)
  const at = sentence.indexOf(verb)
  if (at < 0) return { before: sentence, verb: '', after: '' }
  return { before: sentence.slice(0, at), verb, after: sentence.slice(at + verb.length) }
})
</script>

<template>
  <div class="counterpart" data-testid="family-counterpart-row">
    <AvatarStack :people="members" :size="28" />
    <div v-if="owedMinor === 0" class="counterpart__body">
      <div class="counterpart__name">{{ theirName }}</div>
      <div class="counterpart__state">{{ t('money.allSquare') }}</div>
    </div>
    <div v-else class="counterpart__body">
      <div class="counterpart__sentence">
        {{ sentenceParts.before }}<strong>{{ sentenceParts.verb }}</strong
        >{{ sentenceParts.after }}
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

.counterpart__sentence {
  font-size: var(--text-body);
  color: var(--ink-2);
  line-height: 1.3;
  overflow-wrap: anywhere;
}
</style>
