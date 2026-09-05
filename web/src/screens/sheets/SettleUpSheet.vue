<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import AmountKeypadField from '@/components/AmountKeypadField.vue'
import AmountText from '@/components/AmountText.vue'
import BalanceRow from '@/components/BalanceRow.vue'
import FamilyBalanceCard from '@/components/FamilyBalanceCard.vue'
import FamilyBuilder from '@/components/FamilyBuilder.vue'
import SheetPanel from '@/components/SheetPanel.vue'
import TallyButton from '@/components/TallyButton.vue'
import TextField from '@/components/TextField.vue'
import {
  api,
  type FamiliesView,
  type FamilyView,
  type MemberView,
  type PaybackView,
  type SettlementRow,
} from '@/lib/api'

/**
 * Screen 7 — Settle up. One row per person; Pay files a trip-level settlement that stays
 * PENDING until the person owed (or the trip's creator) confirms it. That confirmation lives here
 * too: a settlement has no bill, so the item sheet never sees it — the recipient approves, rejects
 * or the claimant withdraws it right on this strip. "Done for now" closes the sheet, because
 * all-square is a derived state, never a button (§7a).
 *
 * Also hosts the Family mode toggle (§7b): an ephemeral, viewer-built partition of the whole trip,
 * shown as one card per Family — explicit or auto-singleton — each with one bilateral row per
 * *other* Family. Nothing about it persists; it resets whenever this sheet reopens.
 */
const props = defineProps<{
  open: boolean
  tripId: string
  myMemberId: string
  youAreCreator: boolean
  rows: SettlementRow[]
  /** When opened from a who-owes row's Pay, the person to jump straight into paying. */
  focusMemberId?: string | null
  currencyCode: string
  symbol: string
  members: MemberView[]
}>()
const emit = defineEmits<{ close: []; changed: [] }>()

const { t } = useI18n()

// Same order as the who-owes card: real debts first, all-square people sunk and faded. Two surfaces
// showing the same people in different orders reads as the list having changed under you.
const orderedRows = computed(() => [
  ...props.rows.filter((r) => r.owedMinor !== 0),
  ...props.rows.filter((r) => r.owedMinor === 0),
])

const paying = ref<SettlementRow | null>(null)
const amountMinor = ref(0)
const error = ref('')
const busy = ref(false)
const reminded = ref<string | null>(null)
const rejecting = ref<string | null>(null)
const rejectReason = ref('')

watch(
  () => props.open,
  (open) => {
    if (!open) return
    paying.value = null
    error.value = ''
    reminded.value = null
    rejecting.value = null
    rejectReason.value = ''
    // Family mode is entirely ephemeral (§7b): every reopen starts back on "By person" with
    // nothing built, the same way the rest of this sheet's state resets.
    familyMode.value = false
    builtFamilies.value = []
    buildingFamily.value = false
    familiesView.value = null
    familiesError.value = ''
    familyTicks.value = {}
    // Opened from a specific row's Pay: unfold that person's amount form straight away.
    if (props.focusMemberId) {
      const row = props.rows.find((r) => r.memberId === props.focusMemberId)
      if (row) startPay(row)
    }
  },
)

// Settlements between me and this row's person: ones still waiting on somebody, and ones already
// approved — the latter kept as a muted, undoable record rather than vanishing (§7a). Who may act on
// each is decided by the server (claim.viewerCanDecide / viewerCanUndo), never re-derived here.
const pendingOf = (row: SettlementRow) => row.pending.filter((p) => p.status === 'PENDING')
const settledOf = (row: SettlementRow) => row.settled

// A settlement you filed that they declined — the one place its reason reaches you, since a
// trip-level claim has no bill sheet. Shown only while nothing fresh is pending to this person
// (retrying speaks for itself), and only the newest, which carries the reason they gave.
const declinedOf = (row: SettlementRow): PaybackView[] => {
  // Tolerate an older API that predates the field rather than throwing on it.
  const rejected = row.rejected ?? []
  if (pendingOf(row).length > 0 || rejected.length === 0) return []
  const latest = [...rejected].sort((a, b) => (a.reviewedAt ?? '').localeCompare(b.reviewedAt ?? '')).at(-1)
  return latest ? [latest] : []
}

function startPay(row: SettlementRow) {
  paying.value = row
  // Positive owedMinor is "you owe them" — the amount the Pay button pre-fills.
  amountMinor.value = Math.max(0, row.owedMinor)
  error.value = ''
}

async function act(action: () => Promise<unknown>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await action()
    emit('changed')
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : String(failure)
  } finally {
    busy.value = false
  }
}

async function pay() {
  const row = paying.value
  if (!row || amountMinor.value <= 0) return
  await act(() =>
    api.submitSettlement(props.tripId, { toMemberId: row.memberId, amountMinor: amountMinor.value }),
  )
  if (!error.value) paying.value = null
}

async function remind(row: SettlementRow) {
  await act(() => api.remind(props.tripId, row.memberId))
  if (!error.value) reminded.value = row.memberId
}

async function undoClaim(claim: PaybackView) {
  // Undoing a *settled* payment re-opens the other person's balance, so it asks first; withdrawing
  // your own still-pending one moved nothing, so it does not.
  if (claim.status === 'APPROVED' && !confirm(t('settle.undoConfirm'))) return
  await act(() => api.undoPayback(claim.id))
}
const approveClaim = (paybackId: string) => act(() => api.approvePayback(paybackId))

async function rejectClaim(paybackId: string) {
  if (!rejectReason.value.trim()) return
  await act(() => api.rejectPayback(paybackId, rejectReason.value.trim()))
  if (!error.value) {
    rejecting.value = null
    rejectReason.value = ''
  }
}

// ---- Family mode (§7b): an ephemeral, viewer-built partition of the whole trip. ----

const familyMode = ref(false)
/** Committed explicit Families, in build order — member ids only, never persisted. */
const builtFamilies = ref<string[][]>([])
const buildingFamily = ref(false)
const familiesView = ref<FamiliesView | null>(null)
const familiesError = ref('')

/** Whoever is not yet in a built Family — the builder's candidate list, excluded structurally. */
const unassignedMembers = computed(() => {
  const placed = new Set(builtFamilies.value.flat())
  return props.members.filter((m) => !placed.has(m.id))
})

/** What the builder's *last commit attempt* ticked, keyed by member id — not a live mirror of every
 *  tick, only a snapshot taken at each `onFamilyBuilt`. `FamilyBuilder` seeds its own local state
 *  from this once, on mount: a rejected build's error message is shown from this same screen, and
 *  for reasons not fully pinned down in Vue's reconciliation of the surrounding conditional
 *  siblings, the mere act of that error message appearing was enough to tear down and recreate the
 *  builder — even once nothing it was ever handed as a prop actually changed. Keeping this a snapshot
 *  taken only at commit time, rather than a continuously-synced model, matters: an earlier version
 *  synced it on every tick and, empirically, that made the builder remount on every tick too — far
 *  more disruptive than the rejection case this exists for. Reset where a *new* build session begins. */
const familyTicks = ref<Record<string, boolean>>({})
function startBuildingFamily() {
  familyTicks.value = {}
  buildingFamily.value = true
}

/** Order-independent identity for a set of member ids, so a Family can be matched back to what
 *  built it regardless of the order either side lists its members in. */
function familyKey(ids: string[]): string {
  return [...ids].sort().join(',')
}
const builtKeys = computed(() => new Set(builtFamilies.value.map(familyKey)))
/** Whether this returned Family is one the viewer explicitly built, not an automatic singleton. */
function isBuilt(entry: FamilyView): boolean {
  return builtKeys.value.has(familyKey(entry.members.map((m) => m.id)))
}

/** Bumped on every call, with the value at call-start captured as `seq` below: a response is
 *  applied only if no newer call has started since. `refreshFamilies` has four callers —
 *  `onFamilyBuilt`, `disband`, and both `watch`es below — and nothing otherwise stops an older
 *  call's response from landing after a newer call's and overwriting it with stale data (two
 *  Undo taps in a row, or a `disband` racing a `props.rows` change from elsewhere on the sheet).
 *  Guarding here protects every caller generically, rather than each caller re-inventing it. */
let familiesRequestSeq = 0

/** Defaults to the committed partition; `onFamilyBuilt` passes a candidate that is not committed
 *  yet, so it can be previewed without `builtFamilies` — and therefore `unassignedMembers` and the
 *  builder's `candidates` prop — ever reflecting a build the server has not accepted. */
async function refreshFamilies(preview?: string[][]) {
  const families = preview ?? builtFamilies.value
  const seq = ++familiesRequestSeq
  // Nothing built is not "everyone is their own Family" — it's nothing to show at all. Fetching
  // and rendering N one-person cards here was mathematically correct but read as if switching tabs
  // had silently grouped people; the empty state is what actually communicates "you haven't built
  // anything yet".
  if (families.length === 0) {
    familiesView.value = null
    familiesError.value = ''
    return
  }
  familiesError.value = ''
  try {
    const result = await api.previewFamilies(props.tripId, families)
    // Only the most-recently-issued call may still write: an older one resolving after a newer
    // one has already answered is exactly the stale response this guard exists to drop.
    if (seq === familiesRequestSeq) familiesView.value = result
  } catch (failure) {
    if (seq === familiesRequestSeq) {
      familiesError.value = failure instanceof Error ? failure.message : String(failure)
    }
  }
}

// Family mode is a pure read with no side effect to announce, so it is deliberately never routed
// through act() — act() emits 'changed', which would trigger a full trip refetch on every toggle.
watch(familyMode, (on) => {
  if (on) refreshFamilies()
})
// Balances can move under the sheet (Pay, approve, undo) while Family mode stays open.
watch(
  () => props.rows,
  () => {
    if (familyMode.value) refreshFamilies()
  },
)

async function onFamilyBuilt(memberIds: string[]) {
  // Snapshot what was just ticked *before* anything that might remount the builder (see
  // `familyTicks` above) — if `refreshFamilies` below is what triggers that, the snapshot has to
  // already be in place for the fresh instance to seed itself from.
  familyTicks.value = Object.fromEntries(memberIds.map((id) => [id, true]))
  // Preview the candidate partition without touching `builtFamilies`: a selection covering
  // everyone left is refused by the server (§7b needs 2+ families), and committing it before that
  // is confirmed would leave nothing to undo and nowhere to fix it from. Only commit on success.
  const candidate = [...builtFamilies.value, memberIds]
  await refreshFamilies(candidate)
  if (!familiesError.value) {
    builtFamilies.value = candidate
    buildingFamily.value = false
  }
}

async function disband(entry: FamilyView) {
  const key = familyKey(entry.members.map((m) => m.id))
  builtFamilies.value = builtFamilies.value.filter((family) => familyKey(family) !== key)
  await refreshFamilies()
}
</script>

<template>
  <SheetPanel :open="open" :title="t('trip.settleUp')" @close="emit('close')">
    <div class="settle">
      <div class="settle__mode" role="group">
        <button
          type="button"
          class="settle__mode-btn"
          data-testid="mode-by-person"
          :class="{ 'settle__mode-btn--on': !familyMode }"
          :aria-pressed="!familyMode"
          @click="familyMode = false"
        >
          {{ t('settle.byPerson') }}
        </button>
        <button
          type="button"
          class="settle__mode-btn"
          data-testid="mode-by-family"
          :class="{ 'settle__mode-btn--on': familyMode }"
          :aria-pressed="familyMode"
          @click="familyMode = true"
        >
          {{ t('settle.byFamily') }}
        </button>
      </div>

      <template v-if="!familyMode">
        <div v-for="(row, index) in orderedRows" :key="row.memberId" class="settle__entry">
          <!-- The API row says "positive = you owe them"; BalanceRow speaks the viewer's frame. -->
          <BalanceRow
            :display-name="row.displayName"
            :person-hue="row.personHue"
            :owed-minor="-row.owedMinor"
            :muted="row.owedMinor === 0"
            :currency-code="currencyCode"
            :symbol="symbol"
            :pending="pendingOf(row).length > 0"
            :reminded="reminded === row.memberId"
            :divider="index < orderedRows.length - 1"
            @pay="startPay(row)"
            @remind="remind(row)"
          />

          <div
            v-for="claim in pendingOf(row)"
            :key="claim.id"
            class="settle__pending"
            data-testid="pending-claim"
          >
            <div class="settle__pending-head">
              <span class="settle__pending-text">
                {{
                  claim.fromMemberId === myMemberId
                    ? t('settle.sentForConfirmation', { name: row.displayName })
                    : t('settle.awaitingYou', { name: row.displayName })
                }}
              </span>
              <!-- U1: the amount that is actually waiting, shown — not just that something is. -->
              <AmountText
                :amount-minor="claim.amountMinor"
                size="sm"
                :currency-code="currencyCode"
                :symbol="symbol"
              />
            </div>

            <div class="settle__pending-actions">
              <!-- The claimant withdraws; the recipient (or creator) decides. A settlement's only home
                 is this strip, so both live here — each shown only where the server's flag allows. -->
              <TallyButton
                v-if="claim.fromMemberId === myMemberId"
                size="sm"
                variant="ghost"
                data-testid="pending-undo"
                @click="undoClaim(claim)"
              >
                {{ t('common.cancel') }}
              </TallyButton>
              <template v-if="claim.viewerCanDecide">
                <TallyButton
                  size="sm"
                  variant="secondary"
                  data-testid="pending-reject"
                  @click="rejecting = claim.id"
                >
                  {{ t('settle.reject') }}
                </TallyButton>
                <TallyButton
                  size="sm"
                  variant="primary"
                  data-testid="pending-approve"
                  @click="approveClaim(claim.id)"
                >
                  {{ t('settle.approve') }}
                </TallyButton>
              </template>
            </div>

            <form
              v-if="rejecting === claim.id"
              class="settle__reject"
              @submit.prevent="rejectClaim(claim.id)"
            >
              <TextField
                v-model="rejectReason"
                test-id="pending-reject-reason"
                :placeholder="t('itemDetail.rejectReason')"
              />
              <TallyButton
                size="sm"
                variant="danger"
                data-testid="pending-reject-send"
                :disabled="!rejectReason.trim()"
                @click="rejectClaim(claim.id)"
              >
                {{ t('settle.reject') }}
              </TallyButton>
            </form>
          </div>

          <!-- Approved settlements: already reflected in the balance above, kept as a muted, undoable
             record so a mistaken confirmation is not a one-way door (§7a). -->
          <div
            v-for="claim in settledOf(row)"
            :key="claim.id"
            class="settle__settled"
            data-testid="settled-claim"
          >
            <div class="settle__pending-head">
              <span class="settle__settled-text">
                {{
                  claim.fromMemberId === myMemberId
                    ? t('settle.youPaidThem', { name: row.displayName })
                    : t('settle.theyPaidYou', { name: row.displayName })
                }}
                · {{ t('settle.settled') }}
              </span>
              <AmountText
                :amount-minor="claim.amountMinor"
                size="sm"
                tone="settled"
                :currency-code="currencyCode"
                :symbol="symbol"
              />
            </div>
            <div v-if="claim.viewerCanUndo" class="settle__pending-actions">
              <TallyButton size="sm" variant="ghost" data-testid="settled-undo" @click="undoClaim(claim)">
                {{ t('common.undo') }}
              </TallyButton>
            </div>
          </div>

          <!-- A settlement they declined: the one surface its reason reaches the claimant, a trip-level
             claim having no bill sheet. The row still offers Pay, so a corrected one can be sent. -->
          <div
            v-for="claim in declinedOf(row)"
            :key="claim.id"
            class="settle__declined"
            data-testid="declined-claim"
          >
            <div class="settle__pending-head">
              <span class="settle__declined-text">{{
                t('settle.declinedByThem', { name: row.displayName })
              }}</span>
              <AmountText
                :amount-minor="claim.amountMinor"
                size="sm"
                tone="owe"
                :currency-code="currencyCode"
                :symbol="symbol"
              />
            </div>
            <p v-if="claim.rejectReason" class="settle__declined-reason">"{{ claim.rejectReason }}"</p>
          </div>

          <form
            v-if="paying?.memberId === row.memberId"
            class="settle__pay"
            data-testid="pay-form"
            @submit.prevent="pay"
          >
            <AmountKeypadField
              v-model="amountMinor"
              test-id="pay-amount"
              :currency-code="currencyCode"
              :symbol="symbol"
            />
            <TallyButton
              type="submit"
              variant="primary"
              size="sm"
              data-testid="pay-send"
              :disabled="amountMinor <= 0 || busy"
              @click="pay"
            >
              {{ t('settle.pay') }}
            </TallyButton>
          </form>
        </div>

        <p v-if="error" class="settle__error" role="alert">{{ error }}</p>
      </template>

      <template v-else>
        <FamilyBuilder
          v-if="buildingFamily"
          :initial-ticked="familyTicks"
          :candidates="unassignedMembers"
          :must-leave-one-out="builtFamilies.length === 0"
          @built="onFamilyBuilt"
          @cancel="buildingFamily = false"
        />
        <template v-else>
          <p v-if="builtFamilies.length === 0" class="settle__family-empty" data-testid="no-families-yet">
            {{ t('settle.noFamiliesYet') }}
          </p>
          <div v-else class="settle__families">
            <FamilyBalanceCard
              v-for="entry in familiesView?.families ?? []"
              :key="entry.members.map((m) => m.id).join(',')"
              :members="entry.members"
              :net-minor="entry.netMinor"
              :counterparts="entry.counterparts"
              :removable="isBuilt(entry)"
              :currency-code="currencyCode"
              :symbol="symbol"
              @remove="disband(entry)"
            />
          </div>
          <TallyButton
            v-if="unassignedMembers.length >= 2"
            variant="secondary"
            full-width
            data-testid="build-family"
            @click="startBuildingFamily"
          >
            {{ t('settle.buildFamily') }}
          </TallyButton>
        </template>

        <p v-if="familiesError" class="settle__error" role="alert">{{ familiesError }}</p>
      </template>

      <TallyButton variant="secondary" full-width data-testid="settle-done" @click="emit('close')">{{
        t('common.done')
      }}</TallyButton>
    </div>
  </SheetPanel>
</template>

<style scoped>
.settle {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.settle__entry {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.settle__mode {
  display: flex;
  gap: var(--space-1);
}

.settle__mode-btn {
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

.settle__mode-btn--on {
  border-color: var(--ink);
  background: var(--grape-tint);
  color: var(--ink);
}

.settle__families {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.settle__family-empty {
  font-size: var(--text-caption);
  color: var(--text-muted);
  text-align: center;
  padding: var(--space-4) 0;
}

.settle__pending {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border: var(--border-card);
  border-radius: var(--radius-md);
  background: var(--lemon-tint);
}

.settle__pending-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.settle__pending-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}

.settle__pending-actions:empty {
  display: none;
}

.settle__reject {
  display: flex;
  gap: var(--space-2);
  align-items: center;
}

.settle__pending-text {
  font-size: var(--text-caption);
  color: var(--ink-2);
  overflow-wrap: break-word;
  min-width: 0;
}

/* A settled payment reads as done, not active: muted, sunk back, the way a settled expense row is. */
.settle__settled {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border: var(--border-card);
  border-radius: var(--radius-md);
  background: var(--paper-sunk);
  opacity: 0.72;
}

.settle__settled-text {
  font-size: var(--text-caption);
  color: var(--text-muted);
  overflow-wrap: break-word;
  min-width: 0;
}

/* A declined claim is a dead end that needs explaining, not a live action: sunk like a settled one,
   but its heading in coral to say the payment did not stick, with the reason quoted beneath. */
.settle__declined {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  padding: var(--space-2) var(--space-3);
  border: var(--border-card);
  border-radius: var(--radius-md);
  background: var(--paper-sunk);
}

.settle__declined-text {
  font-size: var(--text-caption);
  font-weight: var(--weight-semibold);
  color: var(--coral);
  overflow-wrap: break-word;
  min-width: 0;
}

.settle__declined-reason {
  font-size: var(--text-caption);
  color: var(--text-muted);
  overflow-wrap: anywhere;
}

.settle__pay {
  /* A column, because the amount is a tappable box with a keypad that unfolds beneath it —
     an inline row once pushed its own button 92px past a 390px viewport. */
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.settle__error {
  color: var(--coral);
  font-size: var(--text-caption);
}
</style>
