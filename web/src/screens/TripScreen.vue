<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import AmountText from '@/components/AmountText.vue'
import AppBar from '@/components/AppBar.vue'
import EmptyState from '@/components/EmptyState.vue'
import ExpenseRow from '@/components/ExpenseRow.vue'
import TallyBadge from '@/components/TallyBadge.vue'
import TallyButton from '@/components/TallyButton.vue'
import TallyCard from '@/components/TallyCard.vue'
import TallyIcon from '@/components/TallyIcon.vue'
import AddExpenseSheet from '@/screens/sheets/AddExpenseSheet.vue'
import ClaimPaybackSheet from '@/screens/sheets/ClaimPaybackSheet.vue'
import EditSplitSheet from '@/screens/sheets/EditSplitSheet.vue'
import InviteSheet from '@/screens/sheets/InviteSheet.vue'
import ItemDetailSheet from '@/screens/sheets/ItemDetailSheet.vue'
import SettleUpSheet from '@/screens/sheets/SettleUpSheet.vue'
import {
  api,
  ApiError,
  apiHref,
  type CategoryView,
  type ItemView,
  type SettlementView,
  type TripView,
} from '@/lib/api'
import { parseLocalDate } from '@/lib/dates'
import { toneOf } from '@/lib/money'

/**
 * Screen 3 — the group. Hero position and the expense feed grouped by day; the add / detail /
 * claim / settle-up sheets all open over this screen and hand back a `changed` event, on which
 * everything is re-fetched — every number here is derived on read by the server, and the one thing
 * this screen must never do is adjust one locally.
 *
 * There is no who-owes-who card (removed 2026-10, spec §3): the hero says where you stand, and the
 * per-person rows with their Pay and Remind live in Settle up → By person. Once people pay along
 * the fewest-transfers plan those rows can cancel without zeroing, and repeating them here read as
 * debts nobody had.
 */
const props = defineProps<{ tripId: string }>()

const { t, locale } = useI18n()
const router = useRouter()

const trip = ref<TripView | null>(null)
const settlement = ref<SettlementView | null>(null)
const categories = ref<CategoryView[]>([])
const filter = ref<'all' | 'unsettled' | 'youPaid'>('all')

const addOpen = ref(false)
const settleOpen = ref(false)
const inviteOpen = ref(false)
const detailItemId = ref<string | null>(null)
const editItem = ref<ItemView | null>(null)
const claim = ref<{ itemId: string; prefillMinor: number } | null>(null)
// Whoever paid the bill being paid back — the claim's recipient, and so whose PayID the sheet shows.
const claimRecipient = computed(() => {
  const payerId = trip.value?.items.find((item) => item.id === claim.value?.itemId)?.payerMemberId
  return trip.value?.members.find((m) => m.id === payerId) ?? null
})

const me = computed(() => trip.value?.members.find((m) => m.isYou) ?? null)
const loadError = ref<'notFound' | 'other' | null>(null)

const heroLabel = computed(() => {
  const net = trip.value?.yourNetMinor ?? 0
  return net === 0 ? t('money.allSquare') : net > 0 ? t('money.youAreOwed') : t('money.youOwe')
})

const FILTER_LABELS = {
  all: 'trip.filterAll',
  unsettled: 'trip.filterUnsettled',
  youPaid: 'trip.filterYouPaid',
}

const filtered = computed(() =>
  (trip.value?.items ?? []).filter((item) => {
    if (filter.value === 'unsettled') return item.state === 'OPEN'
    if (filter.value === 'youPaid') return item.payerMemberId === me.value?.id
    return true
  }),
)

/** Items grouped by calendar day, in the order the server sends them (newest day first). */
const days = computed(() => {
  const groups: { day: string; items: ItemView[] }[] = []
  for (const item of filtered.value) {
    const last = groups.at(-1)
    if (last && last.day === item.spentOn) last.items.push(item)
    else groups.push({ day: item.spentOn, items: [item] })
  }
  return groups
})

function dayLabel(isoDate: string): string {
  return new Intl.DateTimeFormat(locale.value, { weekday: 'short', day: 'numeric', month: 'short' }).format(
    parseLocalDate(isoDate),
  )
}

const memberName = (memberId: string) =>
  trip.value?.members.find((m) => m.id === memberId)?.displayName ?? '?'

const categoryKey = (categoryId: string) => categories.value.find((c) => c.id === categoryId)?.key

async function refresh() {
  const [loadedTrip, loadedSettlement] = await Promise.all([
    api.trip(props.tripId),
    api.settlement(props.tripId),
  ])
  trip.value = loadedTrip
  settlement.value = loadedSettlement
}

async function load() {
  loadError.value = null
  try {
    await refresh()
    categories.value = await api.categories(props.tripId)
  } catch (failure) {
    // A 401 has already sent the router to sign-in. A trip you cannot see 404s — show that. Any
    // other failure is a real error and says so with a retry, rather than the misleading "not here".
    if (failure instanceof ApiError && failure.status === 401) return
    loadError.value = failure instanceof ApiError && failure.status === 404 ? 'notFound' : 'other'
  }
}

onMounted(load)

function startClaimFor(itemId: string, prefillMinor: number) {
  detailItemId.value = null
  claim.value = { itemId, prefillMinor }
}
</script>

<template>
  <main class="trip">
    <AppBar
      :title="trip?.name ?? ''"
      back
      action="user-plus"
      :action-label="t('trip.invite')"
      @back="router.push({ name: 'trips' })"
      @action="inviteOpen = true"
    />

    <template v-if="trip && settlement">
      <TallyCard class="trip__hero">
        <div class="trip__hero-top">
          <div class="trip__hero-position" data-testid="trip-position">
            <p class="trip__label">
              {{ heroLabel }}
              <TallyBadge v-if="trip.closedAt" tone="neutral" data-testid="trip-ended">
                {{ t('trip.ended') }}
              </TallyBadge>
            </p>
            <AmountText
              :amount-minor="Math.abs(trip.yourNetMinor)"
              size="hero"
              :tone="toneOf(trip.yourNetMinor)"
              :currency-code="trip.currencyCode"
            />
          </div>
          <TallyButton variant="primary" size="sm" data-testid="settle-up" @click="settleOpen = true">
            {{ t('trip.settleUp') }}
          </TallyButton>
        </div>
        <dl class="trip__stats">
          <div
            v-for="stat in [
              { label: 'trip.groupSpend', minor: trip.groupSpendMinor },
              { label: 'trip.yourShare', minor: trip.yourShareMinor },
              { label: 'trip.youFronted', minor: trip.youFrontedMinor },
            ]"
            :key="stat.label"
            class="trip__stat"
          >
            <dt>{{ t(stat.label) }}</dt>
            <dd>
              <AmountText :amount-minor="stat.minor" size="sm" :currency-code="trip.currencyCode" />
            </dd>
          </div>
        </dl>
      </TallyCard>

      <section class="trip__expenses">
        <div class="trip__expenses-head">
          <h2 class="trip__section-title">{{ t('trip.expenses') }}</h2>
          <div class="trip__filters" role="group">
            <button
              v-for="option in ['all', 'unsettled', 'youPaid'] as const"
              :key="option"
              type="button"
              class="trip__filter"
              :data-testid="`filter-${option}`"
              :class="{ 'trip__filter--on': filter === option }"
              :aria-pressed="filter === option"
              @click="filter = option"
            >
              {{ t(FILTER_LABELS[option]) }}
            </button>
            <!-- A plain download: the browser fetches the CSV with the session cookie, no JS. The
                 file is the outward spend only — expenses, never the internal who-paid-who. -->
            <a
              class="trip__filter trip__export"
              :href="apiHref(`/api/trips/${tripId}/expenses.csv`)"
              download
              data-testid="export-csv"
            >
              {{ t('trip.exportCsv') }}
            </a>
          </div>
        </div>

        <EmptyState
          v-if="trip.items.length === 0"
          icon="receipt"
          :title="t('trip.empty')"
          :body="t('trip.emptyBody')"
        />

        <div v-for="group in days" :key="group.day" class="trip__day" data-testid="expense-day">
          <h3 class="trip__day-label">{{ dayLabel(group.day) }}</h3>
          <TallyCard>
            <ExpenseRow
              v-for="(item, index) in group.items"
              :key="item.id"
              :title="item.title"
              :category-key="categoryKey(item.categoryId)"
              :paid-by="memberName(item.payerMemberId)"
              :paid-by-you="item.payerMemberId === me?.id"
              :amount-minor="item.amountMinor"
              :all-square="item.state === 'ALL_SQUARE'"
              :currency-code="trip.currencyCode"
              :divider="index < group.items.length - 1"
              @click="detailItemId = item.id"
            />
          </TallyCard>
        </div>
      </section>
    </template>

    <EmptyState
      v-else-if="loadError === 'notFound'"
      class="trip__missing"
      icon="circle-dashed"
      :title="t('trip.notFound')"
      :body="t('trip.notFoundBody')"
      data-testid="trip-missing"
    />

    <EmptyState
      v-else-if="loadError === 'other'"
      class="trip__missing"
      icon="circle-dashed"
      :title="t('trip.loadFailed')"
      :body="''"
      :action="t('trip.retry')"
      data-testid="trip-load-failed"
      @action="load"
    />

    <!-- An ended trip records no new spending, so the button goes away with the ability. -->
    <button
      v-if="trip && settlement && !trip.closedAt"
      class="trip__add"
      type="button"
      data-testid="add-expense"
      :aria-label="t('trip.addExpense')"
      @click="addOpen = true"
    >
      <TallyIcon name="plus" :size="26" />
    </button>

    <AddExpenseSheet
      v-if="trip"
      :open="addOpen"
      :trip="trip"
      :categories="categories"
      @close="addOpen = false"
      @saved="((addOpen = false), refresh())"
    />

    <ItemDetailSheet
      v-if="trip"
      :open="detailItemId !== null"
      :item-id="detailItemId"
      :trip="trip"
      :categories="categories"
      @close="detailItemId = null"
      @changed="refresh"
      @pay-back="startClaimFor"
      @edit="(item) => ((detailItemId = null), (editItem = item))"
    />

    <EditSplitSheet
      v-if="trip"
      :open="editItem !== null"
      :trip="trip"
      :item="editItem"
      :categories="categories"
      @close="editItem = null"
      @saved="((editItem = null), refresh())"
    />

    <InviteSheet v-if="trip" :open="inviteOpen" :trip="trip" @close="inviteOpen = false" @changed="refresh" />

    <ClaimPaybackSheet
      v-if="trip && me"
      :open="claim !== null"
      :item-id="claim?.itemId ?? null"
      :prefill-minor="claim?.prefillMinor ?? 0"
      :recipient="claimRecipient"
      :from-member-id="me.id"
      :currency-code="trip.currencyCode"
      @close="claim = null"
      @saved="((claim = null), refresh())"
    />

    <SettleUpSheet
      v-if="trip && settlement && me"
      :open="settleOpen"
      :trip-id="tripId"
      :my-member-id="me.id"
      :rows="settlement.rows"
      :currency-code="trip.currencyCode"
      :members="trip.members"
      :all-square="settlement.allSquare"
      :transfers="settlement.transfers"
      :breakdown="settlement.breakdown ?? null"
      @close="settleOpen = false"
      @changed="refresh"
    />
  </main>
</template>

<style scoped>
.trip {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding-bottom: calc(var(--space-16) + var(--safe-bottom));
}

.trip__invite-note {
  margin: 0 var(--gutter-screen);
  padding: var(--space-2) var(--space-3);
  border: var(--border-card);
  border-radius: var(--radius-md);
  background: var(--mint-tint);
  font-size: var(--text-caption);
  color: var(--ink-2);
  /* A raw link must wrap rather than clip — nothing here may cut a word off. */
  overflow-wrap: anywhere;
}

.trip__hero {
  margin: 0 var(--gutter-screen);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.trip__hero-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.trip__hero-position {
  min-width: 0;
}

.trip__label {
  font-size: var(--text-label);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--ls-label);
  text-transform: uppercase;
  color: var(--text-muted);
}

.trip__stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-3);
  margin: 0;
}

.trip__stat dt {
  font-size: var(--text-caption);
  color: var(--text-muted);
  /* Labels wrap onto a second line rather than truncate — 中文 and English differ in length. */
  overflow-wrap: break-word;
}

.trip__stat dd {
  margin: var(--space-1) 0 0;
}

.trip__section-title {
  font-size: var(--text-heading-sm);
  font-weight: var(--weight-bold);
  letter-spacing: var(--ls-heading-sm);
  color: var(--ink);
  margin-bottom: var(--space-2);
}

.trip__missing {
  margin: var(--space-8) var(--gutter-screen);
}

.trip__expenses {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin: 0 var(--gutter-screen);
}

.trip__expenses-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.trip__filters {
  display: flex;
  gap: var(--space-1);
}

.trip__filter {
  padding: var(--space-1) var(--space-3);
  border: 2px solid var(--hairline-strong);
  border-radius: var(--radius-pill);
  background: var(--surface-card);
  font-size: var(--text-caption);
  font-weight: var(--weight-semibold);
  color: var(--ink-2);
  cursor: pointer;
  white-space: nowrap;
}

.trip__filter--on {
  border-color: var(--ink);
  background: var(--grape-tint);
  color: var(--ink);
}

/* The export rides in the chip row but is an action, not a filter — same pill, link semantics. */
.trip__export {
  text-decoration: none;
  line-height: normal;
}

.trip__day {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.trip__day-label {
  font-size: var(--text-label);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--ls-label);
  text-transform: uppercase;
  color: var(--text-muted);
}

.trip__add {
  position: fixed;
  right: max(var(--space-5), calc((100vw - 430px) / 2 + var(--space-5)));
  bottom: calc(var(--space-5) + var(--safe-bottom));
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border: 2px solid var(--ink);
  border-radius: var(--radius-circle);
  background: var(--grape);
  color: var(--text-on-accent);
  box-shadow: 0 3px 0 0 var(--ink);
  cursor: pointer;
}

.trip__add:active {
  transform: translateY(2px);
  box-shadow: 0 1px 0 0 var(--ink);
}
</style>
