<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import CopyButton from './CopyButton.vue'
import TallyBadge from './TallyBadge.vue'

/**
 * Where to send somebody money: their PayID as they typed it, and a Copy button — or a plain
 * "(no PayID provided)".
 *
 * Shown, never used. Ledger moves no money and does not validate the value (an email, a phone
 * number, an ABN — the bank checks it, not us), so it is rendered as text, exactly as entered, and
 * never as markup. Sign-in is a bare name, which makes a freshly swapped PayID the one way an
 * impersonator could redirect a payment; the server flags a change in the last 7 days and the badge
 * says so, so whoever is about to pay can check with the person first.
 */
withDefaults(
  defineProps<{
    payId: string | null
    recentlyChanged: boolean
    /** Whose PayID this is, for the Copy button's accessible name. */
    ownerName: string
    /** Off for your own PayID, which you edit (through the default slot) rather than copy. */
    copyable?: boolean
    /**
     * Name the owner in the label ("Ann's PayID") — for a line paid to a Family, where several
     * people could be meant and the money has to reach one of them.
     */
    showOwner?: boolean
    testId?: string
  }>(),
  { copyable: true, showOwner: false, testId: 'payid-line' },
)

const { t } = useI18n()
</script>

<template>
  <div class="payid" :data-testid="testId">
     <span class="payid__label">{{
         showOwner ? t('payId.labelOf', { name: ownerName }) : t('payId.label')
       }}</span>
    <template v-if="payId">
      <span class="payid__value" data-testid="payid-value">{{ payId }}</span>
      <TallyBadge
        v-if="recentlyChanged"
        tone="pending"
        data-testid="payid-recent"
        :title="t('payId.recentlyChangedHint')"
      >
        {{ t('payId.recentlyChanged') }}
      </TallyBadge>
      <CopyButton
        v-if="copyable"
        :text="payId"
        :label="t('payId.copyLabel', { name: ownerName })"
        test-id="payid-copy"
      />
      <slot />
    </template>
    <span v-else class="payid__none" data-testid="payid-none">{{ t('payId.none') }}</span>
  </div>
</template>

<style scoped>
/* Wraps rather than scrolls: a long PayID breaks across lines inside a 390px sheet, and the Copy
   button drops below it when the two cannot share a line. */
.payid {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1) var(--space-2);
  min-width: 0;
}

.payid__label {
  font-size: var(--text-caption);
  font-weight: var(--weight-bold);
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--text-subtle);
}

.payid__value {
  flex: 0 1 auto;
  min-width: 0;
  font-weight: var(--weight-semibold);
  color: var(--ink);
  overflow-wrap: anywhere;
}

.payid__none {
  font-size: var(--text-caption);
  color: var(--text-muted);
}
</style>
