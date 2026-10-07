<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import AmountKeypadField from './AmountKeypadField.vue'
import TallyButton from './TallyButton.vue'

/**
 * Settle up's pay form: an amount and a Pay button. The default slot goes above the amount — where
 * the money goes, when the caller has that to show.
 */
defineProps<{ currencyCode: string; loading: boolean }>()
const amountMinor = defineModel<number>({ required: true })
const emit = defineEmits<{ pay: [] }>()

const { t } = useI18n()
</script>

<template>
  <form class="pay" data-testid="pay-form" @submit.prevent="emit('pay')">
    <slot />
    <AmountKeypadField v-model="amountMinor" test-id="pay-amount" :currency-code="currencyCode" />
    <TallyButton
      type="submit"
      variant="primary"
      size="sm"
      data-testid="pay-send"
      :loading="loading"
      :disabled="amountMinor <= 0"
      @click="emit('pay')"
    >
      {{ t('settle.pay') }}
    </TallyButton>
  </form>
</template>

<style scoped>
.pay {
  /* A column, because the amount is a tappable box with a keypad that unfolds beneath it —
     an inline row once pushed its own button 92px past a 390px viewport. */
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
</style>
