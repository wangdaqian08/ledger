<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import AmountText from '@/components/AmountText.vue'
import EmptyState from '@/components/EmptyState.vue'
import GroupCard from '@/components/GroupCard.vue'
import LocaleToggle from '@/components/LocaleToggle.vue'
import ProgressBar from '@/components/ProgressBar.vue'
import SheetPanel from '@/components/SheetPanel.vue'
import TallyButton from '@/components/TallyButton.vue'
import TallyCard from '@/components/TallyCard.vue'
import TextField from '@/components/TextField.vue'
import TallyIcon from '@/components/TallyIcon.vue'
import { api, ApiError } from '@/lib/api'
import { currencySymbol } from '@/lib/money'
import { useSession } from '@/stores/session'
import { useTrips } from '@/stores/trips'

/**
 * Screen 2 — GroupsHome. Every group as a card, with the overall position above them.
 *
 * Live groups first, then the ones that have ended, then — for whoever deleted them — what can
 * still be brought back. The sections exist because a list that only ever grows stops being about
 * the trip you are on now.
 */
const { t, locale } = useI18n()
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

// Three lists out of one payload. The server sends every trip you are on and lets the screen
// decide what to show, which is why hiding can be a listing decision here without a hidden trip's
// balance quietly vanishing from the totals above.
const live = computed(() => (overview.value?.trips ?? []).filter((trip) => !trip.closedAt))
const completed = computed(() => (overview.value?.trips ?? []).filter((trip) => trip.closedAt))
const putAway = computed(() => completed.value.filter((trip) => trip.hiddenAt))
const onShow = computed(() => completed.value.filter((trip) => !trip.hiddenAt))
const deleted = computed(() => overview.value?.deleted ?? [])

const revealHidden = ref(false)
const restoring = ref(false)
const restoreError = ref('')
const loadError = ref(false)

// Put-away trips sit under the ones still on show, so revealing them never reorders what was
// already there.
const shownCompleted = computed(() =>
  revealHidden.value ? [...onShow.value, ...putAway.value] : onShow.value,
)

// The reader's own locale, same as every other date in the app — two date orders on one screen
// would read as a mistake.
const purgeDate = (iso: string | null) =>
  iso
    ? new Intl.DateTimeFormat(locale.value, { year: 'numeric', month: 'short', day: 'numeric' }).format(
        new Date(iso),
      )
    : ''

async function restore(tripId: string) {
  if (restoring.value) return
  restoring.value = true
  restoreError.value = ''
  try {
    await api.restoreTrip(tripId)
    await trips.loadOverview()
  } catch (failure) {
    restoreError.value = failure instanceof ApiError ? failure.message : String(failure)
  } finally {
    restoring.value = false
  }
}

// One figure per currency: ¥ added to $ is a meaningless number, so each stands on its own line
// with its own symbol. The server sends the per-currency breakdown already summed.
const toneFor = (net: number) => (net === 0 ? 'settled' : net > 0 ? 'owed' : 'owe')
const labelFor = (net: number) =>
  net === 0 ? t('money.allSquare') : net > 0 ? t('money.youAreOwed') : t('money.youOwe')

async function load() {
  loadError.value = false
  try {
    await Promise.all([trips.loadOverview(), session.checked ? Promise.resolve() : session.load()])
  } catch (failure) {
    // A 401 has already sent the router to sign-in. Anything else must not vanish into an unhandled
    // rejection and leave a permanently blank page — surface it with a retry, as TripScreen does.
    if (failure instanceof ApiError && failure.status === 401) return
    loadError.value = true
  }
}

onMounted(load)

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
      <div class="trips__bar-right">
        <LocaleToggle />
        <TallyButton variant="ghost" size="sm" data-testid="sign-out" @click="signOut">{{
          t('trips.signOut')
        }}</TallyButton>
      </div>
    </header>

    <TallyCard v-if="loadError && !overview" class="trips__hero" data-testid="trips-error">
      <p class="trips__error" role="alert">{{ t('trips.loadFailed') }}</p>
      <TallyButton variant="secondary" data-testid="trips-retry" @click="load">{{
        t('trips.retry')
      }}</TallyButton>
    </TallyCard>

    <TallyCard v-if="overview && overview.overalls.length > 0" class="trips__hero" data-testid="overall-hero">
      <div v-for="total in overview.overalls" :key="total.currencyCode" class="trips__overall">
        <!-- The ISO code rides beside each line: two trips in different dollars are both "$", and the
             overall screen's one job is telling the viewer which money each figure is. -->
        <p class="trips__hero-label">{{ labelFor(total.netMinor) }} · {{ total.currencyCode }}</p>
        <AmountText
          :amount-minor="Math.abs(total.netMinor)"
          size="hero"
          :tone="toneFor(total.netMinor)"
          :currency-code="total.currencyCode"
          :symbol="currencySymbol(total.currencyCode)"
        />
      </div>
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
        <TallyButton variant="secondary" size="sm" data-testid="new-group" @click="creating = true">
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
        v-for="trip in live"
        :key="trip.id"
        data-testid="group-card"
        :name="trip.name"
        :icon="trip.icon"
        :hue="trip.hue"
        :members="trip.members.map((m) => ({ id: m.id, displayName: m.displayName, personHue: m.personHue }))"
        :your-net-minor="trip.yourNetMinor"
        :currency-code="trip.currencyCode"
        :symbol="currencySymbol(trip.currencyCode)"
        @click="router.push({ name: 'trip', params: { tripId: trip.id } })"
      />

      <!-- Every group finished is a real state now that completed ones file below; without this
           line the heading would just sit over silence. -->
      <p
        v-if="overview && live.length === 0 && completed.length > 0"
        class="trips__quiet"
        data-testid="live-empty"
      >
        {{ t('trips.liveEmpty') }}
      </p>
    </section>

    <!-- Ended trips, in their own section so the list above stays about the trip you are on now.
         Put-away ones live behind the toggle: hiding is tidying, not secrecy, so anybody looking
         at this screen can see what was put away and open it. -->
    <section v-if="completed.length > 0" class="trips__list" data-testid="completed-section">
      <div class="trips__list-head">
        <h2 class="trips__title">{{ t('trips.completed') }}</h2>
        <TallyButton
          v-if="putAway.length > 0"
          variant="ghost"
          size="sm"
          data-testid="toggle-hidden"
          :aria-expanded="revealHidden"
          @click="revealHidden = !revealHidden"
        >
          {{ revealHidden ? t('trips.hideHidden') : t('trips.showHidden', { count: putAway.length }) }}
        </TallyButton>
      </div>

      <GroupCard
        v-for="trip in shownCompleted"
        :key="trip.id"
        data-testid="group-card"
        :class="{ 'trips__card--away': !!trip.hiddenAt }"
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

    <!-- Only ever populated for the person who did the deleting; for everyone else the trip
         simply ended. Each row carries the date it stops being restorable, because a promise of
         "30 days" that the reader has to count themselves is not a promise. -->
    <section v-if="deleted.length > 0" class="trips__list" data-testid="deleted-section">
      <h2 class="trips__title">{{ t('trips.recentlyDeleted') }}</h2>
      <div v-for="trip in deleted" :key="trip.id" class="trips__binned" data-testid="deleted-trip">
        <TallyIcon :name="trip.icon" :size="20" />
        <div class="trips__binned-text">
          <span class="trips__binned-name">{{ trip.name }}</span>
          <span class="trips__binned-note">{{
            t('trips.restorableUntil', { date: purgeDate(trip.purgesAt) })
          }}</span>
        </div>
        <TallyButton
          variant="secondary"
          size="sm"
          data-testid="restore-trip"
          :disabled="restoring"
          @click="restore(trip.id)"
        >
          {{ t('trips.restore') }}
        </TallyButton>
      </div>
      <p v-if="restoreError" class="trips__error" role="alert">{{ restoreError }}</p>
    </section>

    <SheetPanel :open="creating" :title="t('trips.newGroup')" @close="creating = false">
      <form class="trips__create" @submit.prevent="create">
        <TextField v-model="newName" test-id="group-name" :label="t('trips.name')" :disabled="busy" />
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
          data-testid="create-group"
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

.trips__bar-right {
  display: flex;
  align-items: center;
  gap: var(--space-2);
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

.trips__overall {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
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

/* Put away, not gone: muted enough to read as filed away, but no further — the card's smallest
   text has to stay comfortably readable, which 0.62 did not leave it. */
.trips__card--away {
  opacity: 0.75;
}

.trips__quiet {
  font-size: var(--text-caption);
  color: var(--text-muted);
}

.trips__binned {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  border: var(--border-card);
  border-radius: var(--radius-md);
  background: var(--surface-card);
  color: var(--text-muted);
}

.trips__binned-text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.trips__binned-name {
  font-weight: var(--weight-semibold);
  color: var(--ink-2);
  overflow-wrap: anywhere;
}

.trips__binned-note {
  font-size: var(--text-caption);
  color: var(--text-muted);
}
</style>
