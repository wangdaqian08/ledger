/**
 * The one place the app talks HTTP.
 *
 * Sessions ride in an HttpOnly cookie, so there is no token handling here — only the CSRF echo:
 * Spring writes LEDGER-XSRF as a readable cookie (named for this app, because another Spring app
 * shares the production host and two XSRF-TOKENs on one page make writes a coin toss) and expects
 * it back in a header on every write. Errors arrive as RFC 7807 problem details; `detail` is the
 * human sentence the server composed, and it is the message screens show, because the server is
 * the one that knows why it refused.
 *
 * Amounts are integer minor units in every type below. There is no float money on the wire and
 * none in the app.
 */

/**
 * Everything below is written root-relative and prefixed here: in production the app lives under
 * a sub-path (`/ledger`), and the build's base is the single fact that moves it there.
 */
const API_BASE = import.meta.env.BASE_URL.replace(/\/$/, '')

/** For plain hrefs (downloads) that must reach the API without going through [request]. */
export const apiHref = (path: string): string => API_BASE + path

/**
 * The `<img>` src for an expense's receipt: same-origin and cookie-authenticated, so it needs no
 * JS at all. The version rides as a cache-buster — the server answers `immutable`, and a replace
 * mints a new version, so the old cache entry is simply never asked for again.
 */
export const receiptHref = (itemId: string, version: string): string =>
  apiHref(`/api/items/${itemId}/receipt`) + `?v=${encodeURIComponent(version)}`

export class ApiError extends Error {
  constructor(
    readonly status: number,
    detail: string,
  ) {
    super(detail)
  }
}

/** Called on any 401 so the router can hand the person to the sign-in screen. */
let onUnauthorized: (() => void) | null = null
export function handleUnauthorized(handler: () => void) {
  onUnauthorized = handler
}

function csrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)LEDGER-XSRF=([^;]*)/)
  return match?.[1] ? decodeURIComponent(match[1]) : null
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  // A FormData body sets its own multipart Content-Type with the boundary in it; stamping one
  // here would strip the boundary and hand the server an unreadable request.
  const form = body instanceof FormData
  if (body !== undefined && !form) headers['Content-Type'] = 'application/json'
  const token = csrfToken()
  if (token) headers['X-XSRF-TOKEN'] = token

  const response = await fetch(API_BASE + path, {
    method,
    headers,
    body: body === undefined ? undefined : form ? body : JSON.stringify(body),
    credentials: 'same-origin',
  })

  if (response.status === 401) {
    onUnauthorized?.()
    throw new ApiError(401, 'Signed out')
  }
  if (!response.ok) {
    const detail = await response
      .json()
      .then((problem: { detail?: string }) => problem.detail)
      .catch(() => undefined)
    throw new ApiError(response.status, detail ?? `Request failed (${response.status})`)
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

// ---- Shapes, mirrored from the server's *View types. Field for field, no more. ----

export interface MemberView {
  id: string
  displayName: string
  personHue: number
  claimed: boolean
  isYou: boolean
}

export interface SplitView {
  memberId: string
  amountMinor: number
  weight: number | null
  exactAmountMinor: number | null
}

export type SplitRule = 'EQUAL' | 'WEIGHTED' | 'EXACT'
export type ItemState = 'OPEN' | 'ALL_SQUARE'

/** The pointer to an expense's receipt image; the bytes live behind [receiptHref]. */
export interface ReceiptView {
  /** Rotates on every replace — it is the cache-busting half of the image URL. */
  version: string
}

export interface ItemView {
  id: string
  tripId: string
  title: string
  categoryId: string
  amountMinor: number
  splitRule: SplitRule
  payerMemberId: string
  spentOn: string
  note: string | null
  splits: SplitView[]
  yourShareMinor: number
  state: ItemState
  receipt: ReceiptView | null
}

export interface TripView {
  id: string
  name: string
  icon: string
  hue: number
  currencyCode: string
  startsOn: string | null
  endsOn: string | null
  members: MemberView[]
  items: ItemView[]
  yourNetMinor: number
  /**
   * What the whole group still has open: every positive net summed. The delete dialog reads this
   * rather than the viewer's own net, so a host who is personally square is still told what the
   * rest of the group has unsettled before they erase it.
   */
  unsettledMinor: number
  /** The three headline figures, derived by the engine so no screen recomputes them. */
  groupSpendMinor: number
  yourShareMinor: number
  youFrontedMinor: number
  /** Whether the viewer created the trip — gates the creator's edit / approve / roster powers. */
  youAreCreator: boolean
  /**
   * When the creator ended the trip; null while it is live. Ended trips take no expense changes
   * (the UI hides those affordances rather than offering a 409), settle up as normal, and lose
   * their receipt images 14 days on.
   */
  closedAt: string | null
  /**
   * When the creator put an ended trip away, off every member's home list; null while it is on
   * them. Only GroupsHome reads this — a hidden trip opens, settles and counts like any other
   * ended one, so nothing else has any business behaving differently.
   */
  hiddenAt: string | null
}

/** A trip its creator deleted and can still bring back, until [purgesAt] passes. */
export interface DeletedTripView {
  id: string
  name: string
  icon: string
  hue: number
  deletedAt: string | null
  purgesAt: string | null
}

/** One overall total per currency the viewer holds a trip in — never summed across currencies. */
export interface CurrencyTotal {
  currencyCode: string
  netMinor: number
}

export interface TripsView {
  trips: TripView[]
  overalls: CurrencyTotal[]
  settledTripCount: number
  /**
   * What you deleted and can still restore. Empty for everyone else, and defaulted here because
   * an older server omits the field entirely — the section simply does not appear.
   */
  deleted?: DeletedTripView[]
}

export type PaybackStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface PaybackView {
  id: string
  itemId: string | null
  fromMemberId: string
  toMemberId: string
  amountMinor: number
  paidOn: string
  note: string | null
  status: PaybackStatus
  proofObjectName: string | null
  rejectReason: string | null
  reviewedAt: string | null
  /**
   * What the viewer may do with this claim, decided by the server (not re-derived here): whether
   * they may approve/reject it, and whether they may undo it. A control only shows where its flag
   * is set, so the UI can never offer a button the server will refuse.
   */
  viewerCanDecide: boolean
  viewerCanUndo: boolean
}

export type ItemDetail = ItemView & { paybacks: PaybackView[] }

export interface SettlementRow {
  memberId: string
  displayName: string
  personHue: number
  /** Positive: you owe them. Negative: they owe you. */
  owedMinor: number
  /** Trip-level settlements between you and this person that nobody has decided yet. */
  pending: PaybackView[]
  /** Approved settlements between you two — a visible, undoable record (they already moved owedMinor). */
  settled: PaybackView[]
  /** Settlements you filed that they declined, newest last — carries the reason, so it doesn't vanish. */
  rejected: PaybackView[]
}

export interface SettlementView {
  rows: SettlementRow[]
  yourNetMinor: number
  allSquare: boolean
}

export interface CategoryView {
  id: string
  key: string
  nameEn: string
  nameZh: string
  icon: string
  hue: number
  builtIn: boolean
}

export interface MeView {
  id: string
  displayName: string
  email: string
  photoUrl: string | null
  friends: { id: string; displayName: string; photoUrl: string | null; sharedTripCount: number }[]
}

export interface InviteView {
  token: string
  expiresAt: string
}

/** The roster a share link is allowed to show before its holder is on the trip. */
export interface ClaimableView {
  tripName: string
  /** The viewer's own seat when they already hold one — the join screen then offers the trip, not the list. */
  you: { id: string; displayName: string; personHue: number } | null
  members: { id: string; displayName: string; personHue: number }[]
}

export interface ShareInput {
  memberId: string
  weight?: number
  exactAmountMinor?: number
}

export interface CreateItemBody {
  /** Minted by the client: it is the split's salt, which is what makes the preview exact. */
  id: string
  title: string
  categoryId: string
  amountMinor: number
  splitRule: SplitRule
  payerMemberId: string
  spentOn: string
  note?: string
  sharedBy: ShareInput[]
}

export interface PatchItemBody {
  title?: string
  categoryId?: string
  amountMinor?: number
  splitRule?: SplitRule
  payerMemberId?: string
  spentOn?: string
  note?: string
  sharedBy?: ShareInput[]
}

// ---- Calls, one per endpoint the seven screens use. ----

export const api = {
  signIn: (idToken: string) => request<MeView>('POST', '/api/auth/session', { idToken }),
  signOut: () => request<void>('DELETE', '/api/auth/session'),
  me: () => request<MeView>('GET', '/api/me'),

  trips: () => request<TripsView>('GET', '/api/trips'),
  createTrip: (body: { name: string; icon: string; hue: number; currencyCode: string }) =>
    request<TripView>('POST', '/api/trips', body),
  trip: (tripId: string) => request<TripView>('GET', `/api/trips/${tripId}`),
  invite: (tripId: string) => request<InviteView>('POST', `/api/trips/${tripId}/invite`, {}),
  hideTrip: (tripId: string) => request<TripView>('POST', `/api/trips/${tripId}/hide`, {}),
  unhideTrip: (tripId: string) => request<TripView>('POST', `/api/trips/${tripId}/unhide`, {}),
  deleteTrip: (tripId: string) => request<void>('DELETE', `/api/trips/${tripId}`),
  restoreTrip: (tripId: string) => request<TripView>('POST', `/api/trips/${tripId}/restore`, {}),
  claimable: (tripId: string, token: string) =>
    request<ClaimableView>('POST', `/api/trips/${tripId}/claimable`, { token }),
  claim: (tripId: string, token: string, memberId: string) =>
    request<TripView>('POST', `/api/trips/${tripId}/claim`, { token, memberId }),
  addMember: (tripId: string, displayName: string) =>
    request<MemberView>('POST', `/api/trips/${tripId}/members`, { displayName }),
  renameMember: (tripId: string, memberId: string, displayName: string) =>
    request<MemberView>('PATCH', `/api/trips/${tripId}/members/${memberId}`, { displayName }),
  closeTrip: (tripId: string) => request<TripView>('POST', `/api/trips/${tripId}/close`, {}),
  reopenTrip: (tripId: string) => request<TripView>('POST', `/api/trips/${tripId}/reopen`, {}),

  categories: (tripId: string) => request<CategoryView[]>('GET', `/api/trips/${tripId}/categories`),
  createItem: (tripId: string, body: CreateItemBody) =>
    request<ItemView>('POST', `/api/trips/${tripId}/items`, body),
  itemDetail: (itemId: string) => request<ItemDetail>('GET', `/api/items/${itemId}`),
  // Partial on purpose: a field left out is a field left alone. This is where the people list
  // gets fixed — the hotel case's whole mechanism (spec §6).
  patchItem: (itemId: string, body: PatchItemBody) =>
    request<ItemView>('PATCH', `/api/items/${itemId}`, body),
  deleteItem: (itemId: string) => request<void>('DELETE', `/api/items/${itemId}`),

  uploadReceipt: (itemId: string, image: Blob, filename: string) => {
    const form = new FormData()
    form.append('file', image, filename)
    return request<ItemView>('POST', `/api/items/${itemId}/receipt`, form)
  },
  deleteReceipt: (itemId: string) => request<void>('DELETE', `/api/items/${itemId}/receipt`),

  submitItemPayback: (
    itemId: string,
    body: { fromMemberId: string; amountMinor: number; paidOn: string; note?: string },
  ) => request<PaybackView>('POST', `/api/items/${itemId}/paybacks`, body),
  approvePayback: (paybackId: string) =>
    request<PaybackView>('POST', `/api/paybacks/${paybackId}/approve`, {}),
  rejectPayback: (paybackId: string, reason: string) =>
    request<PaybackView>('POST', `/api/paybacks/${paybackId}/reject`, { reason }),
  undoPayback: (paybackId: string) => request<void>('POST', `/api/paybacks/${paybackId}/undo`, {}),

  settlement: (tripId: string) => request<SettlementView>('GET', `/api/trips/${tripId}/settlement`),
  submitSettlement: (tripId: string, body: { toMemberId: string; amountMinor: number }) =>
    request<PaybackView>('POST', `/api/trips/${tripId}/settlements`, body),
  remind: (tripId: string, memberId: string) =>
    request<void>('POST', `/api/trips/${tripId}/remind`, { memberId }),
}
