<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import AmountText from '@/components/AmountText.vue'
import EmptyState from '@/components/EmptyState.vue'
import GroupCard from '@/components/GroupCard.vue'
import ProgressBar from '@/components/ProgressBar.vue'
import SheetPanel from '@/components/SheetPanel.vue'
import TallyButton from '@/components/TallyButton.vue'
import TallyCard from '@/components/TallyCard.vue'
import TextField from '@/components/TextField.vue'
import { ApiError } from '@/lib/api'
import { currencySymbol } from '@/lib/money'
import { useSession } from '@/stores/session'
import { useTrips } from '@/stores/trips'

/** Screen 2 — GroupsHome. Every group as a card, with the overall position above them. */
const { t } = useI18n()
const router = useRouter()
const session = useSession()
const trips = useTrips()

const error = ref('')
const creating = ref(false)
const newName = ref('')
const newCurrency = ref('AUD')
const busy = ref(false)

const CURRENCIES = ['AUD', 'USD', 'EUR', 'GBP', 'JPY', 'CNY']
// Groups get the eight hues round-robin in creation order, same rule the server uses for people.
const GROUP_ICONS = [
  'plane',
  'house',
  'coffee',
  'ticket',
  'car-front',
  'utensils',
  'bed-double',
  'party-popper',
]

const overview = computed(() => trips.overview)
const overallTone = computed(() => {
  const net = overview.value?.overallNetMinor ?? 0
  return net === 0 ? 'settled' : net > 0 ? 'owed' : 'owe'
})
const overallLabel = computed(() => {
  const net = overview.value?.overallNetMinor ?? 0
  return net === 0 ? t('money.allSquare') : net > 0 ? t('money.youAreOwed') : t('money.youOwe')
})

onMounted(async () => {
  try {
    await Promise.all([trips.loadOverview(), session.checked ? Promise.resolve() : session.load()])
  } catch (failure) {
    // A 401 has already sent the router to sign-in; anything else is worth the console noise.
    if (!(failure instanceof ApiError && failure.status === 401)) throw failure
  }
})

async function create() {
  if (!newName.value.trim() || busy.value) return
  busy.value = true
  error.value = ''
  try {
    const count = overview.value?.trips.length ?? 0
    const trip = await trips.createTrip({
      name: newName.value.trim(),
      icon: GROUP_ICONS[count % GROUP_ICONS.length]!,
      hue: (count % 8) + 1,
      currencyCode: newCurrency.value,
    })
    creating.value = false
    newName.value = ''
    await router.push({ name: 'trip', params: { tripId: trip.id } })
  } catch (failure) {
    error.value = failure instanceof ApiError ? failure.message : String(failure)
  } finally {
    busy.value = false
  }
}

async function signOut() {
  await session.signOut()
  await router.push({ name: 'signin' })
}
</script>

<template>
  <main class="trips">
    <header class="trips__bar">
      <h1 class="trips__brand">{{ t('signin.title') }}</h1>
      <TallyButton variant="ghost" size="sm" @click="signOut">{{ t('trips.signOut') }}</TallyButton>
    </header>

    <TallyCard v-if="overview" class="trips__hero">
      <p class="trips__hero-label">{{ overallLabel }}</p>
      <AmountText
        :amount-minor="Math.abs(overview.overallNetMinor)"
        size="hero"
        :tone="overallTone"
        currency-code="AUD"
      />
      <ProgressBar
        v-if="overview.trips.length > 0"
        :covered-minor="overview.settledTripCount"
        :of-minor="overview.trips.length"
        tone="mint"
        :label="t('trips.groupsSettled', { count: overview.settledTripCount, total: overview.trips.length })"
      />
    </TallyCard>

    <section class="trips__list">
      <div class="trips__list-head">
        <h2 class="trips__title">{{ t('trips.title') }}</h2>
        <TallyButton variant="secondary" size="sm" @click="creating = true">
          {{ t('trips.newGroup') }}
        </TallyButton>
      </div>

      <EmptyState
        v-if="overview && overview.trips.length === 0"
        icon="users"
        :title="t('trips.empty')"
        :body="t('trips.emptyBody')"
        :action="t('trips.newGroup')"
        @action="creating = true"
      />

      <GroupCard
        v-for="trip in overview?.trips ?? []"
        :key="trip.id"
        :name="trip.name"
        :icon="trip.icon"
        :hue="trip.hue"
        :members="trip.members.map((m) => ({ id: m.id, displayName: m.displayName, personHue: m.personHue }))"
        :your-net-minor="trip.yourNetMinor"
        :currency-code="trip.currencyCode"
        :symbol="currencySymbol(trip.currencyCode)"
        @click="router.push({ name: 'trip', params: { tripId: trip.id } })"
      />
    </section>

    <SheetPanel :open="creating" :title="t('trips.newGroup')" @close="creating = false">
      <form class="trips__create" @submit.prevent="create">
        <TextField v-model="newName" :label="t('trips.name')" :disabled="busy" />
        <fieldset class="trips__currencies">
          <legend class="trips__legend">{{ t('trips.currency') }}</legend>
          <button
            v-for="code in CURRENCIES"
            :key="code"
            type="button"
            class="trips__currency"
            :class="{ 'trips__currency--on': newCurrency === code }"
            :aria-pressed="newCurrency === code"
            @click="newCurrency = code"
          >
            {{ code }}
          </button>
        </fieldset>
        <p v-if="error" class="trips__error" role="alert">{{ error }}</p>
        <TallyButton
          type="submit"
          variant="primary"
          full-width
          :disabled="!newName.trim() || busy"
          @click="create"
        >
          {{ t('trips.create') }}
        </TallyButton>
      </form>
    </SheetPanel>
  </main>
</template>

<style scoped>
.trips {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  padding: var(--space-4) var(--gutter-screen) var(--space-12);
}

.trips__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.trips__brand {
  font-family: var(--font-core);
  font-size: var(--text-heading-lg);
  font-weight: var(--weight-black);
  letter-spacing: var(--ls-heading-lg);
  color: var(--ink);
}

.trips__hero {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.trips__hero-label {
  font-size: var(--text-label);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--ls-label);
  text-transform: uppercase;
  color: var(--text-muted);
}

.trips__list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.trips__list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.trips__title {
  font-size: var(--text-heading);
  font-weight: var(--weight-bold);
  color: var(--ink);
}

.trips__create {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.trips__currencies {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  border: none;
  padding: 0;
}

.trips__legend {
  width: 100%;
  margin-bottom: var(--space-2);
  font-size: var(--text-label);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--ls-label);
  text-transform: uppercase;
  color: var(--text-muted);
}

.trips__currency {
  padding: var(--space-2) var(--space-3);
  border: 2px solid var(--hairline-strong);
  border-radius: var(--radius-chip);
  background: var(--surface-card);
  font-family: var(--font-money);
  font-weight: var(--weight-semibold);
  color: var(--ink-2);
  cursor: pointer;
  white-space: nowrap;
}

.trips__currency--on {
  border-color: var(--ink);
  background: var(--grape-tint);
  color: var(--ink);
}

.trips__error {
  color: var(--coral);
  font-size: var(--text-caption);
}
</style>
