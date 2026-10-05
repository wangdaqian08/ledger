<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AmountKeypadField from '@/components/AmountKeypadField.vue'
import AmountText from '@/components/AmountText.vue'
import BalanceRow from '@/components/BalanceRow.vue'
import FamilyBalanceCard from '@/components/FamilyBalanceCard.vue'
import FamilyBuilder from '@/components/FamilyBuilder.vue'
import PayIdLine from '@/components/PayIdLine.vue'
import SheetPanel from '@/components/SheetPanel.vue'
import TallyButton from '@/components/TallyButton.vue'
import TallyIcon from '@/components/TallyIcon.vue'
import TextField from '@/components/TextField.vue'
import {
  api,
  type BreakdownView,
  type FamiliesView,
  type FamilyMemberView,
  type FamilyTransferView,
  type FamilyView,
  type MemberView,
  type PaybackView,
  type SettlementRow,
  type TransferView,
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
 *
 * And "By minimum transfer": the whole trip's fewest-transfers plan, exactly as the server derived
 * and ordered it — numbered, each line with where to send the money (the recipient's PayID). Only
 * your own transfers carry Pay, because a settlement is always filed by the person paying; it is
 * the same PENDING claim as Pay on a row, so approving it stays on the By person strip (§7a).
 * Paying by transfers settles nets, not pairs, which is why "square" means your net is 0: the rows
 * can then still read non-zero while cancelling out, and they fade with nothing left to act on.
 *
 * With Families built under "By family", the plan honours them: a Family settles among itself, so
 * as one party it pays or gets paid once rather than each member squaring up separately. That plan
 * comes back with the Family preview, already ordered, with the member a payment on each line goes
 * to chosen by the server. Building nothing leaves the per-person plan exactly as it was, and the
 * Families are still ephemeral — nothing is saved, and they reset with the rest of the sheet.
 * Any claim already filed on a family line hides Pay from every member of the paying Family, because
 * a second member paying the same line would be the Family paying twice.
 *
 * Under every mode, folded away until asked for, "How it adds up": the server's per-person
 * breakdown — paid, share, settled, balance, transfers by person → fewest — and its totals, so
 * anybody can check the arithmetic by hand. Rendered as sent; no column is ever summed here.
 */
const props = withDefaults(
  defineProps<{
    open: boolean
    tripId: string
    myMemberId: string
    youAreCreator: boolean
    rows: SettlementRow[]
    /** A person to jump straight into paying on open. Nothing passes one today: the trip screen's
     *  who-owes card that did is gone, and its Settle up button opens on nobody. */
    focusMemberId?: string | null
    currencyCode: string
    symbol: string
    members: MemberView[]
    /** The server's verdict that the viewer's overall net is 0 — never re-derived from the rows. */
    allSquare?: boolean
    /** The trip's fewest-transfers plan, in the server's order. */
    transfers?: TransferView[]
    /** "How it adds up", as the server derived it. Absent from an older server: no section then. */
    breakdown?: BreakdownView | null
  }>(),
  { focusMemberId: null, allSquare: false, transfers: () => [], breakdown: null },
)
const emit = defineEmits<{ close: []; changed: [] }>()

const { t } = useI18n()

// Real debts first, all-square people sunk and faded, so a $0 row never sits above money that still
// needs acting on.
const orderedRows = computed(() => [
  ...props.rows.filter((r) => r.owedMinor !== 0),
  ...props.rows.filter((r) => r.owedMinor === 0),
])

/** Whose pay form is unfolded, by member id — a By person row's or a transfer's recipient. */
const paying = ref<string | null>(null)
const amountMinor = ref(0)
const error = ref('')
const busy = ref(false)
const pendingTag = ref<string | null>(null)
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
    // Every mode is a view, and Family mode is entirely ephemeral (§7b): every reopen starts back
    // on "By person" with nothing built, the same way the rest of this sheet's state resets.
    mode.value = 'person'
    builtFamilies.value = []
    buildingFamily.value = false
    familiesView.value = null
    familiesError.value = ''
    familyTicks.value = {}
    breakdownOpen.value = false
    // Opened from a specific row's Pay: unfold that person's amount form straight away — unless
    // the viewer is square overall, where the rows cancel out and there is nothing to pay.
    if (props.focusMemberId && !props.allSquare) {
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
  paying.value = row.memberId
  // Positive owedMinor is "you owe them" — the amount the Pay button pre-fills.
  amountMinor.value = Math.max(0, row.owedMinor)
  error.value = ''
}

/** Square overall while some row is not: those rows cancel out, so none of them offers an action. */
const squareOverall = computed(() => props.allSquare && props.rows.some((r) => r.owedMinor !== 0))

const memberById = (memberId: string) => props.members.find((m) => m.id === memberId)
const memberName = (memberId: string) => memberById(memberId)?.displayName ?? '?'

async function act(action: () => Promise<unknown>, tag: string | null = null) {
  if (busy.value) return
  busy.value = true
  pendingTag.value = tag
  error.value = ''
  try {
    await action()
    emit('changed')
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : String(failure)
  } finally {
    busy.value = false
    pendingTag.value = null
  }
}

async function pay() {
  const toMemberId = paying.value
  if (!toMemberId || amountMinor.value <= 0) return
  await act(() => api.submitSettlement(props.tripId, { toMemberId, amountMinor: amountMinor.value }), 'pay')
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

// ---- Modes. Each is a view of the same trip; switching is never a change worth announcing. ----

type Mode = 'person' | 'family' | 'transfers'
const mode = ref<Mode>('person')

function setMode(next: Mode) {
  if (mode.value === next) return
  mode.value = next
  // A half-filled pay form belongs to the view it was opened in, not to the next one.
  paying.value = null
  error.value = ''
}

// ---- By minimum transfer: the server's plan, rendered as sent. ----

/** "You pay Cat" / "Dan pays you" / "Dan pays Ben" — the viewer is always "you", grammatically. */
function transferSentence(transfer: TransferView): string {
  if (transfer.fromMemberId === props.myMemberId) {
    return t('settle.transferYouPay', { to: memberName(transfer.toMemberId) })
  }
  if (transfer.toMemberId === props.myMemberId) {
    return t('settle.transferPaysYou', { from: memberName(transfer.fromMemberId) })
  }
  return t('settle.transferPays', {
    from: memberName(transfer.fromMemberId),
    to: memberName(transfer.toMemberId),
  })
}

/**
 * A claim already waiting on this transfer, if the viewer is one end of it: one they sent (so Pay
 * would be a second payment), or one sent to them (which they decide on the By person strip).
 * Only the viewer's own pairs are in `rows`, so a transfer between two others never has one here.
 */
function pendingOnTransfer(transfer: TransferView): { claim: PaybackView; sentByYou: boolean } | null {
  const mine = transfer.fromMemberId === props.myMemberId
  if (!mine && transfer.toMemberId !== props.myMemberId) return null
  const other = mine ? transfer.toMemberId : transfer.fromMemberId
  const row = props.rows.find((r) => r.memberId === other)
  const claim = row ? pendingOf(row).find((p) => p.fromMemberId === transfer.fromMemberId) : undefined
  return claim ? { claim, sentByYou: mine } : null
}

function startTransferPay(transfer: TransferView) {
  paying.value = transfer.toMemberId
  amountMinor.value = transfer.amountMinor
  error.value = ''
}

// ---- Family mode (§7b): an ephemeral, viewer-built partition of the whole trip. ----

const familyMode = computed(() => mode.value === 'family')
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
 *  builder's `candidates` prop — ever reflecting a build the server has not accepted.
 *
 *  Resolves true only when this call's own answer was applied: false when it failed, and false when
 *  a newer call overtook it, since a dropped answer says nothing about what this call asked for. */
async function refreshFamilies(preview?: string[][]): Promise<boolean> {
  const families = preview ?? builtFamilies.value
  const seq = ++familiesRequestSeq
  // Nothing built is not "everyone is their own Family" — it's nothing to show at all. Fetching
  // and rendering N one-person cards here was mathematically correct but read as if switching tabs
  // had silently grouped people; the empty state is what actually communicates "you haven't built
  // anything yet".
  if (families.length === 0) {
    familiesView.value = null
    familiesError.value = ''
    return true
  }
  familiesError.value = ''
  try {
    const result = await api.previewFamilies(props.tripId, families)
    // Only the most-recently-issued call may still write: an older one resolving after a newer
    // one has already answered is exactly the stale response this guard exists to drop.
    if (seq !== familiesRequestSeq) return false
    familiesView.value = result
    return true
  } catch (failure) {
    if (seq === familiesRequestSeq) {
      familiesError.value = failure instanceof Error ? failure.message : String(failure)
    }
    return false
  }
}

/** "By minimum transfer" with Families built: its plan is the preview's, not the `transfers` prop. */
const familyPlanMode = computed(() => mode.value === 'transfers' && builtFamilies.value.length > 0)

// Both views of the partition are a pure read with no side effect to announce, so they are
// deliberately never routed through act() — act() emits 'changed', which would trigger a full trip
// refetch on every toggle.
watch(mode, () => {
  if (familyMode.value || familyPlanMode.value) refreshFamilies()
})
// Balances can move under the sheet (Pay, approve, undo) while either view stays open.
watch(
  () => props.rows,
  () => {
    if (familyMode.value || familyPlanMode.value) refreshFamilies()
  },
)

async function onFamilyBuilt(memberIds: string[]) {
  // Snapshot what was just ticked *before* anything that might remount the builder (see
  // `familyTicks` above) — if `refreshFamilies` below is what triggers that, the snapshot has to
  // already be in place for the fresh instance to seed itself from.
  familyTicks.value = Object.fromEntries(memberIds.map((id) => [id, true]))
  // Preview the candidate partition without touching `builtFamilies`: a selection covering
  // everyone left is refused by the server (§7b needs 2+ families), and committing it before that
  // is confirmed would leave nothing to undo and nowhere to fix it from. Only commit on this
  // preview's own answer — not one a newer fetch overtook (switching to the plan mid-build, say),
  // which would leave `builtFamilies` naming a partition `familiesView` was never computed for.
  const candidate = [...builtFamilies.value, memberIds]
  if (await refreshFamilies(candidate)) {
    builtFamilies.value = candidate
    buildingFamily.value = false
  }
}

async function disband(entry: FamilyView) {
  const key = familyKey(entry.members.map((m) => m.id))
  builtFamilies.value = builtFamilies.value.filter((family) => familyKey(family) !== key)
  await refreshFamilies()
}

// ---- By minimum transfer with Families: the server's family plan, rendered as sent. ----

/** The family plan once it has answered. Null while it loads or after it failed — and never the
 *  per-person plan in its place, which ignores the Families and so would be wrong to pay from. */
const familyPlan = computed<FamilyTransferView[] | null>(() =>
  familiesView.value && !familiesError.value ? familiesView.value.transfers : null,
)
/** How many transfers the plan on screen has; null while there is no plan yet to count. */
const planSize = computed<number | null>(() =>
  familyPlanMode.value ? (familyPlan.value?.length ?? null) : props.transfers.length,
)

/** Folds names pairwise through a "{a} … {b}" message, so each language owns its separator. */
function joinWith(key: string, parts: string[]): string {
  return parts.slice(1).reduce((a, b) => t(key, { a, b }), parts[0] ?? '')
}

/** A party by name, the viewer first: "You & Dan" leading a sentence, "you & Ben" after a verb. */
function partyLabel(people: Pick<FamilyMemberView, 'id' | 'displayName'>[], subject: boolean): string {
  const others = people.filter((m) => m.id !== props.myMemberId).map((m) => m.displayName)
  if (others.length === people.length) return joinWith('settle.partyJoin', others)
  return joinWith('settle.partyJoin', [subject ? t('common.you') : t('settle.partyYouObject'), ...others])
}

/** "Using your families: Ann & Ben · You & Dan" — what the viewer built, in the order they built it. */
const familiesNote = computed(() =>
  t('settle.transferFamilies', {
    families: joinWith(
      'settle.familiesJoin',
      builtFamilies.value.map((ids) =>
        partyLabel(
          ids.map((id) => ({ id, displayName: memberName(id) })),
          true,
        ),
      ),
    ),
  }),
)

/** "Dan pays Ann & Ben" / "You & Cat pay Ann" / "Dan pays you & Ben": the verb agrees with what the
 *  reader sees — "pays" only for a single person who is not the viewer. */
function familyTransferSentence(transfer: FamilyTransferView): string {
  const single = transfer.from.length === 1 && transfer.from[0]?.id !== props.myMemberId
  return t(single ? 'settle.transferPays' : 'settle.transferPayMany', {
    from: partyLabel(transfer.from, true),
    to: partyLabel(transfer.to, false),
  })
}

const familyLineKey = (transfer: FamilyTransferView) =>
  `${transfer.from.map((m) => m.id).join(',')}>${transfer.to.map((m) => m.id).join(',')}`
/** Whether the viewer is in the paying party — a settlement is always filed by somebody paying. */
const youPayOn = (transfer: FamilyTransferView) => transfer.from.some((m) => m.id === props.myMemberId)

function startFamilyTransferPay(transfer: FamilyTransferView) {
  paying.value = transfer.payToMemberId
  amountMinor.value = transfer.amountMinor
  error.value = ''
}

// ---- How it adds up: the server's breakdown, rendered as sent. ----

/** Folded by default, and folded again on every reopen with the rest of the sheet's state. */
const breakdownOpen = ref(false)
const breakdownId = useId()

/** Settled is a column only once something has been settled — a comparison, never a sum. */
const showSettled = computed(
  () =>
    !!props.breakdown &&
    (props.breakdown.totals.settledMinor !== 0 || props.breakdown.rows.some((r) => r.settledMinor !== 0)),
)
/** Paid, share, [settled], balance: the numbers line's column count, which the grid is cut to. */
const breakdownColumns = computed(() => (showSettled.value ? 4 : 3))

/** A balance's colour, from its sign alone — the same three tones as every other balance here. */
const balanceTone = (minor: number) => (minor === 0 ? 'settled' : minor > 0 ? 'owed' : 'owe')
</script>

<template>
  <SheetPanel :open="open" :title="t('trip.settleUp')" @close="emit('close')">
    <div class="settle">
      <div class="settle__mode" role="group">
        <button
          type="button"
          class="settle__mode-btn"
          data-testid="mode-by-person"
          :class="{ 'settle__mode-btn--on': mode === 'person' }"
          :aria-pressed="mode === 'person'"
          @click="setMode('person')"
        >
          {{ t('settle.byPerson') }}
        </button>
        <button
          type="button"
          class="settle__mode-btn"
          data-testid="mode-by-family"
          :class="{ 'settle__mode-btn--on': mode === 'family' }"
          :aria-pressed="mode === 'family'"
          @click="setMode('family')"
        >
          {{ t('settle.byFamily') }}
        </button>
        <button
          type="button"
          class="settle__mode-btn"
          data-testid="mode-min-transfer"
          :class="{ 'settle__mode-btn--on': mode === 'transfers' }"
          :aria-pressed="mode === 'transfers'"
          @click="setMode('transfers')"
        >
          {{ t('settle.byMinTransfer') }}
        </button>
      </div>

      <template v-if="mode === 'person'">
        <p v-if="squareOverall" class="settle__square" data-testid="square-overall">
          {{ t('settle.squareOverall') }}
        </p>
        <div v-for="(row, index) in orderedRows" :key="row.memberId" class="settle__entry">
          <!-- The API row says "positive = you owe them"; BalanceRow speaks the viewer's frame. -->
          <BalanceRow
            :display-name="row.displayName"
            :person-hue="row.personHue"
            :owed-minor="-row.owedMinor"
            :muted="squareOverall || row.owedMinor === 0"
            :actions="!squareOverall"
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
            v-if="paying === row.memberId"
            class="settle__pay"
            data-testid="pay-form"
            @submit.prevent="pay"
          >
            <!-- Where the money goes, right where the amount is entered. -->
            <PayIdLine
              :pay-id="memberById(row.memberId)?.payId ?? null"
              :recently-changed="memberById(row.memberId)?.payIdChangedRecently ?? false"
              :owner-name="row.displayName"
            />
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
              :loading="pendingTag === 'pay'"
              :disabled="amountMinor <= 0"
              @click="pay"
            >
              {{ t('settle.pay') }}
            </TallyButton>
          </form>
        </div>

        <p v-if="error" class="settle__error" role="alert">{{ error }}</p>
      </template>

      <template v-else-if="mode === 'transfers'">
        <p v-if="familyPlanMode" class="settle__transfer-families" data-testid="transfer-families">
          {{ familiesNote }}
        </p>
        <p v-if="planSize === 0" class="settle__family-empty" data-testid="no-transfers">
          {{ t('settle.noTransfers') }}
        </p>
        <template v-else-if="planSize !== null">
          <p class="settle__transfer-count" data-testid="transfer-count">
            {{
              planSize === 1
                ? t('settle.transferCountOne', { count: planSize })
                : t('settle.transferCount', { count: planSize })
            }}
          </p>
          <!-- Each side a Family or a lone person, rendered as the server sent it. -->
          <ol v-if="familyPlanMode" class="settle__transfers">
            <li
              v-for="(transfer, index) in familyPlan ?? []"
              :key="familyLineKey(transfer)"
              class="settle__transfer"
              data-testid="transfer-row"
            >
              <div class="settle__transfer-head">
                <span class="settle__transfer-index">{{ index + 1 }}</span>
                <span class="settle__transfer-text">{{ familyTransferSentence(transfer) }}</span>
                <AmountText
                  :amount-minor="transfer.amountMinor"
                  :currency-code="currencyCode"
                  :symbol="symbol"
                />
              </div>

              <!-- Whoever the server picked to receive this line's payment. -->
              <PayIdLine
                class="settle__transfer-payid"
                test-id="transfer-payid"
                :pay-id="memberById(transfer.payToMemberId)?.payId ?? null"
                :recently-changed="memberById(transfer.payToMemberId)?.payIdChangedRecently ?? false"
                :owner-name="memberName(transfer.payToMemberId)"
                :show-owner="transfer.to.length > 1"
              />

              <!-- Already claimed by anyone in the paying Family: read-only, and Pay hidden from every
                   one of them — a second member paying this line would be the Family paying twice. -->
              <template v-if="transfer.pending.length > 0">
                <div
                  v-for="claim in transfer.pending"
                  :key="claim.id"
                  class="settle__pending settle__transfer-note"
                  data-testid="transfer-pending"
                >
                  <div class="settle__pending-head">
                    <span class="settle__pending-text">
                      {{
                        claim.fromMemberId === myMemberId
                          ? t('settle.sentForConfirmation', { name: memberName(claim.toMemberId) })
                          : t('settle.familySentForConfirmation', { name: memberName(claim.fromMemberId) })
                      }}
                    </span>
                    <AmountText
                      :amount-minor="claim.amountMinor"
                      size="sm"
                      :currency-code="currencyCode"
                      :symbol="symbol"
                    />
                  </div>
                </div>
              </template>

              <template v-else-if="youPayOn(transfer)">
                <form
                  v-if="paying === transfer.payToMemberId"
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
                    :loading="pendingTag === 'pay'"
                    :disabled="amountMinor <= 0"
                    @click="pay"
                  >
                    {{ t('settle.pay') }}
                  </TallyButton>
                </form>
                <TallyButton
                  v-else
                  class="settle__transfer-pay"
                  size="sm"
                  data-testid="transfer-pay"
                  @click="startFamilyTransferPay(transfer)"
                >
                  {{ t('settle.pay') }}
                </TallyButton>
              </template>
            </li>
          </ol>
          <ol v-else class="settle__transfers">
            <li
              v-for="(transfer, index) in transfers"
              :key="`${transfer.fromMemberId}-${transfer.toMemberId}`"
              class="settle__transfer"
              data-testid="transfer-row"
            >
              <div class="settle__transfer-head">
                <span class="settle__transfer-index">{{ index + 1 }}</span>
                <span class="settle__transfer-text">{{ transferSentence(transfer) }}</span>
                <AmountText
                  :amount-minor="transfer.amountMinor"
                  :currency-code="currencyCode"
                  :symbol="symbol"
                />
              </div>

              <PayIdLine
                class="settle__transfer-payid"
                test-id="transfer-payid"
                :pay-id="memberById(transfer.toMemberId)?.payId ?? null"
                :recently-changed="memberById(transfer.toMemberId)?.payIdChangedRecently ?? false"
                :owner-name="memberName(transfer.toMemberId)"
              />

              <!-- Already claimed: read-only here. Withdrawing or deciding it is the By person
                   strip's job, so a settlement keeps exactly one place it is acted on (§7a). -->
              <div
                v-if="pendingOnTransfer(transfer)"
                class="settle__pending settle__transfer-note"
                data-testid="transfer-pending"
              >
                <div class="settle__pending-head">
                  <span class="settle__pending-text">
                    {{
                      pendingOnTransfer(transfer)!.sentByYou
                        ? t('settle.sentForConfirmation', { name: memberName(transfer.toMemberId) })
                        : t('settle.awaitingYou', { name: memberName(transfer.fromMemberId) })
                    }}
                  </span>
                  <AmountText
                    :amount-minor="pendingOnTransfer(transfer)!.claim.amountMinor"
                    size="sm"
                    :currency-code="currencyCode"
                    :symbol="symbol"
                  />
                </div>
              </div>

              <template v-else-if="transfer.fromMemberId === myMemberId">
                <form
                  v-if="paying === transfer.toMemberId"
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
                    :loading="pendingTag === 'pay'"
                    :disabled="amountMinor <= 0"
                    @click="pay"
                  >
                    {{ t('settle.pay') }}
                  </TallyButton>
                </form>
                <TallyButton
                  v-else
                  class="settle__transfer-pay"
                  size="sm"
                  data-testid="transfer-pay"
                  @click="startTransferPay(transfer)"
                >
                  {{ t('settle.pay') }}
                </TallyButton>
              </template>
            </li>
          </ol>
        </template>

        <p v-if="familyPlanMode && familiesError" class="settle__error" role="alert">{{ familiesError }}</p>
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

      <!-- How it adds up: under every mode, folded until asked for. Every figure is the server's — the
           totals line is its totals, never the columns summed here. -->
      <section v-if="breakdown" class="settle__sums">
        <button
          type="button"
          class="settle__sums-toggle"
          data-testid="breakdown-toggle"
          :aria-expanded="breakdownOpen"
          :aria-controls="breakdownId"
          @click="breakdownOpen = !breakdownOpen"
        >
          <span class="settle__sums-label">{{ t('breakdown.toggle') }}</span>
          <TallyIcon
            name="chevron-right"
            :size="16"
            class="settle__sums-chevron"
            :class="{ 'settle__sums-chevron--open': breakdownOpen }"
          />
        </button>

        <!-- Something for aria-controls to point at whether or not the table is up. -->
        <div :id="breakdownId" class="settle__sums-panel">
          <div v-if="breakdownOpen" class="sums" data-testid="breakdown">
            <!-- Each person is two lines on one grid: name and transfers, then the money columns. Six
                 columns side by side do not fit 390px with real amounts; four under a name do. -->
            <div
              class="sums__table"
              role="table"
              :aria-label="t('breakdown.toggle')"
              :style="{ '--sums-cols': breakdownColumns }"
            >
              <div class="sums__line sums__line--head" role="row">
                <span class="sums__name" role="columnheader">{{ t('breakdown.person') }}</span>
                <span class="sums__num" role="columnheader">{{ t('breakdown.paid') }}</span>
                <span class="sums__num" role="columnheader">{{ t('breakdown.share') }}</span>
                <span
                  v-if="showSettled"
                  class="sums__num"
                  role="columnheader"
                  data-testid="breakdown-head-settled"
                  >{{ t('breakdown.settled') }}</span
                >
                <span class="sums__num" role="columnheader">{{ t('breakdown.balance') }}</span>
                <span class="sums__transfers" role="columnheader">{{ t('breakdown.transfers') }}</span>
              </div>

              <div
                v-for="row in breakdown.rows"
                :key="row.memberId"
                class="sums__line"
                role="row"
                data-testid="breakdown-row"
              >
                <span
                  class="sums__name"
                  role="rowheader"
                  :title="row.displayName"
                  data-testid="breakdown-name"
                  >{{ row.isYou ? t('common.you') : row.displayName }}</span
                >
                <span class="sums__num" role="cell" data-testid="breakdown-paid">
                  <AmountText
                    :amount-minor="row.paidMinor"
                    size="xs"
                    :currency-code="currencyCode"
                    :symbol="symbol"
                  />
                </span>
                <span class="sums__num" role="cell" data-testid="breakdown-share">
                  <AmountText
                    :amount-minor="row.shareMinor"
                    size="xs"
                    :currency-code="currencyCode"
                    :symbol="symbol"
                  />
                </span>
                <span v-if="showSettled" class="sums__num" role="cell" data-testid="breakdown-settled">
                  <AmountText
                    :amount-minor="row.settledMinor"
                    size="xs"
                    :show-sign="row.settledMinor !== 0"
                    :currency-code="currencyCode"
                    :symbol="symbol"
                  />
                </span>
                <span class="sums__num" role="cell" data-testid="breakdown-balance">
                  <AmountText
                    :amount-minor="row.netMinor"
                    size="xs"
                    :tone="balanceTone(row.netMinor)"
                    :show-sign="row.netMinor !== 0"
                    :currency-code="currencyCode"
                    :symbol="symbol"
                  />
                </span>
                <span class="sums__transfers" role="cell" data-testid="breakdown-transfers">{{
                  t('breakdown.transferPair', {
                    byPerson: row.transfersByPerson,
                    fewest: row.transfersFewest,
                  })
                }}</span>
              </div>

              <div class="sums__line sums__line--total" role="row" data-testid="breakdown-total">
                <span class="sums__name" role="rowheader" data-testid="breakdown-name">{{
                  t('breakdown.total')
                }}</span>
                <span class="sums__num" role="cell" data-testid="breakdown-paid">
                  <AmountText
                    :amount-minor="breakdown.totals.paidMinor"
                    size="xs"
                    :currency-code="currencyCode"
                    :symbol="symbol"
                  />
                </span>
                <span class="sums__num" role="cell" data-testid="breakdown-share">
                  <AmountText
                    :amount-minor="breakdown.totals.shareMinor"
                    size="xs"
                    :currency-code="currencyCode"
                    :symbol="symbol"
                  />
                </span>
                <span v-if="showSettled" class="sums__num" role="cell" data-testid="breakdown-settled">
                  <AmountText
                    :amount-minor="breakdown.totals.settledMinor"
                    size="xs"
                    :show-sign="breakdown.totals.settledMinor !== 0"
                    :currency-code="currencyCode"
                    :symbol="symbol"
                  />
                </span>
                <span class="sums__num" role="cell" data-testid="breakdown-balance">
                  <AmountText
                    :amount-minor="breakdown.totals.netMinor"
                    size="xs"
                    :tone="balanceTone(breakdown.totals.netMinor)"
                    :show-sign="breakdown.totals.netMinor !== 0"
                    :currency-code="currencyCode"
                    :symbol="symbol"
                  />
                </span>
                <span class="sums__transfers" role="cell" data-testid="breakdown-transfers">{{
                  t('breakdown.transferPair', {
                    byPerson: breakdown.totals.transfersByPerson,
                    fewest: breakdown.totals.transfersFewest,
                  })
                }}</span>
              </div>
            </div>

            <p class="sums__legend" data-testid="breakdown-legend">{{ t('breakdown.legend') }}</p>
          </div>
        </div>
      </section>

      <TallyButton variant="secondary" full-width data-testid="settle-done" @click="emit('close')"
        >{{ t('common.done') }}
      </TallyButton>
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
  /* Three pills do not fit one 390px line in every language; the last wraps rather than poking out. */
  flex-wrap: wrap;
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

.settle__square {
  font-size: var(--text-caption);
  color: var(--text-muted);
}

.settle__transfer-count {
  font-size: var(--text-caption);
  font-weight: var(--weight-semibold);
  color: var(--ink-2);
}

.settle__transfer-families {
  font-size: var(--text-caption);
  color: var(--text-muted);
  overflow-wrap: anywhere;
}

.settle__transfers {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.settle__transfer {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding-bottom: var(--space-3);
  border-bottom: 1.5px solid var(--hairline);
}

.settle__transfer:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.settle__transfer-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.settle__transfer-index {
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
  width: 24px;
  height: 24px;
  border: 2px solid var(--ink);
  border-radius: var(--radius-circle);
  font-size: var(--text-caption);
  font-weight: var(--weight-bold);
  color: var(--ink);
}

.settle__transfer-text {
  flex: 1;
  min-width: 0;
  font-weight: var(--weight-semibold);
  color: var(--ink);
  overflow-wrap: anywhere;
}

/* Indented under the sentence, past the number disc, so each line reads as one unit. */
.settle__transfer-payid,
.settle__transfer-note,
.settle__transfer-pay {
  margin-left: calc(24px + var(--space-2));
}

.settle__transfer-pay {
  align-self: flex-start;
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

/* ---- How it adds up ---- */

.settle__sums {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding-top: var(--space-3);
  border-top: 1.5px solid var(--hairline);
}

.settle__sums-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  width: 100%;
  min-height: 32px;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  text-align: left;
}

/* The same uppercase micro-label as the comment fold, so it reads as a section, not a stray control. */
.settle__sums-label {
  font-size: var(--text-label);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--ls-label);
  text-transform: uppercase;
  color: var(--text-muted);
}

/* icons.ts has no chevron-down: the shared chevron-right, turned a quarter each way (as CommentField). */
.settle__sums-chevron {
  transition: transform var(--dur-fast) var(--ease-out);
  transform: rotate(90deg);
  color: var(--ink-2);
}

.settle__sums-chevron--open {
  transform: rotate(-90deg);
}

/* An anchor for aria-controls and nothing else: its children lay out as if it were not there. */
.settle__sums-panel {
  display: contents;
}

.sums {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

/*
 * One grid per line, cut into as many equal columns as there are money figures (3, or 4 with
 * Settled). Line one is the name across all but the last column, transfers in the last; line two is
 * the money, one figure a column. minmax(0, 1fr) keeps every column inside the sheet: nothing here
 * may push the page — or the sheet — sideways at 390px. Four columns there are ~83px each, which is
 * why the figures are AmountText's dense `xs`: "−$6,172.83" fits with room to spare even in the
 * monospace fallback, not only once the webfont has arrived.
 */
.sums__line {
  display: grid;
  grid-template-columns: repeat(var(--sums-cols), minmax(0, 1fr));
  column-gap: var(--space-2);
  row-gap: 2px;
  padding: var(--space-2) 0;
  border-bottom: 1.5px solid var(--hairline);
}

.sums__line--head {
  padding-top: 0;
  font-size: var(--text-label);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--ls-label);
  text-transform: uppercase;
  color: var(--text-muted);
}

/* The totals read as the sum line of a ledger: a firmer rule above, nothing below. */
.sums__line--total {
  border-top: 1.5px solid var(--ink);
  border-bottom: none;
}

.sums__name {
  grid-row: 1;
  grid-column: 1 / -2;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sums__line:not(.sums__line--head) .sums__name {
  font-size: var(--text-caption);
  font-weight: var(--weight-bold);
  color: var(--ink);
}

.sums__transfers {
  grid-row: 1;
  grid-column: -2 / -1;
  min-width: 0;
  text-align: right;
  white-space: nowrap;
}

.sums__line:not(.sums__line--head) .sums__transfers {
  font-family: var(--font-money);
  font-size: var(--text-caption);
  font-variant-numeric: tabular-nums;
  color: var(--ink-2);
}

.sums__num {
  grid-row: 2;
  min-width: 0;
  text-align: right;
}

.sums__legend {
  font-size: var(--text-caption);
  color: var(--text-muted);
  overflow-wrap: break-word;
}
</style>
