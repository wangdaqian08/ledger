/**
 * The one place the app talks HTTP.
 *
 * Sessions ride in an HttpOnly cookie, so there is no token handling here — only the CSRF echo:
 * Spring writes XSRF-TOKEN as a readable cookie and expects it back in a header on every write.
 * Errors arrive as RFC 7807 problem details; `detail` is the human sentence the server composed,
 * and it is the message screens show, because the server is the one that knows why it refused.
 *
 * Amounts are integer minor units in every type below. There is no float money on the wire and
 * none in the app.
 */

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
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/)
  return match?.[1] ? decodeURIComponent(match[1]) : null
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = csrfToken()
  if (token) headers['X-XSRF-TOKEN'] = token

  const response = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
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
}

export interface TripsView {
  trips: TripView[]
  overallNetMinor: number
  settledTripCount: number
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
}

export type ItemDetail = ItemView & { paybacks: PaybackView[] }

export interface SettlementRow {
  memberId: string
  displayName: string
  personHue: number
  /** Positive: you owe them. Negative: they owe you. */
  owedMinor: number
  pending: PaybackView[]
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
  claimable: (tripId: string, token: string) =>
    request<ClaimableView>('POST', `/api/trips/${tripId}/claimable`, { token }),
  claim: (tripId: string, token: string, memberId: string) =>
    request<TripView>('POST', `/api/trips/${tripId}/claim`, { token, memberId }),
  addMember: (tripId: string, displayName: string) =>
    request<MemberView>('POST', `/api/trips/${tripId}/members`, { displayName }),

  categories: (tripId: string) => request<CategoryView[]>('GET', `/api/trips/${tripId}/categories`),
  createItem: (tripId: string, body: CreateItemBody) =>
    request<ItemView>('POST', `/api/trips/${tripId}/items`, body),
  itemDetail: (itemId: string) => request<ItemDetail>('GET', `/api/items/${itemId}`),
  // Partial on purpose: a field left out is a field left alone. This is where the people list
  // gets fixed — the hotel case's whole mechanism (spec §6).
  patchItem: (itemId: string, body: PatchItemBody) =>
    request<ItemView>('PATCH', `/api/items/${itemId}`, body),
  deleteItem: (itemId: string) => request<void>('DELETE', `/api/items/${itemId}`),

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
