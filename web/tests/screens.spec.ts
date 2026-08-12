import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import SheetPanel from '../src/components/SheetPanel.vue'
import AddExpenseSheet from '../src/screens/sheets/AddExpenseSheet.vue'
import ClaimPaybackSheet from '../src/screens/sheets/ClaimPaybackSheet.vue'
import AmountKeypadField from '../src/components/AmountKeypadField.vue'
import EditSplitSheet from '../src/screens/sheets/EditSplitSheet.vue'
import InviteSheet from '../src/screens/sheets/InviteSheet.vue'
import ItemDetailSheet from '../src/screens/sheets/ItemDetailSheet.vue'
import SettleUpSheet from '../src/screens/sheets/SettleUpSheet.vue'
import JoinScreen from '../src/screens/JoinScreen.vue'
import SignInScreen from '../src/screens/SignInScreen.vue'
import TripScreen from '../src/screens/TripScreen.vue'
import TripsScreen from '../src/screens/TripsScreen.vue'
import { findAllByTestId, findByTestId } from './testids'
import { saltFor, splitShares } from '../src/lib/split'
import type {
  ItemView,
  MemberView,
  PaybackView,
  SettlementRow,
  SettlementView,
  TripView,
} from '../src/lib/api'

/**
 * The screens against a scripted API. Every number a screen shows must be traceable to the
 * fixture that produced it — these tests assert the wiring, while the engine contract and the
 * server suites own the arithmetic itself.
 */
vi.mock('../src/lib/api', async () => {
  const actual = await vi.importActual<typeof import('../src/lib/api')>('../src/lib/api')
  return {
    ...actual,
    api: {
      signIn: vi.fn(),
      signOut: vi.fn(),
      me: vi.fn(),
      trips: vi.fn(),
      createTrip: vi.fn(),
      trip: vi.fn(),
      invite: vi.fn(),
      claimable: vi.fn(),
      claim: vi.fn(),
      addMember: vi.fn(),
      categories: vi.fn(),
      createItem: vi.fn(),
      itemDetail: vi.fn(),
      patchItem: vi.fn(),
      deleteItem: vi.fn(),
      submitItemPayback: vi.fn(),
      approvePayback: vi.fn(),
      rejectPayback: vi.fn(),
      undoPayback: vi.fn(),
      settlement: vi.fn(),
      submitSettlement: vi.fn(),
      remind: vi.fn(),
    },
  }
})

const { api } = await import('../src/lib/api')
const mocked = api as unknown as Record<string, ReturnType<typeof vi.fn>>

// ---- Fixtures: one small trip, stated once. ----

const you: MemberView = { id: 'm-you', displayName: 'Alice', personHue: 1, claimed: true, isYou: true }
const bob: MemberView = { id: 'm-bob', displayName: 'Bob', personHue: 2, claimed: true, isYou: false }
const cara: MemberView = { id: 'm-cara', displayName: 'Cara', personHue: 3, claimed: false, isYou: false }

function item(overrides: Partial<ItemView>): ItemView {
  return {
    id: 'i-1',
    tripId: 't-1',
    title: 'Dinner',
    categoryId: 'c-food',
    amountMinor: 9_000,
    splitRule: 'EQUAL',
    payerMemberId: you.id,
    spentOn: '2026-08-07',
    note: null,
    splits: [
      { memberId: you.id, amountMinor: 3_000, weight: null, exactAmountMinor: null },
      { memberId: bob.id, amountMinor: 3_000, weight: null, exactAmountMinor: null },
      { memberId: cara.id, amountMinor: 3_000, weight: null, exactAmountMinor: null },
    ],
    yourShareMinor: 3_000,
    state: 'OPEN',
    ...overrides,
  }
}

function trip(overrides: Partial<TripView> = {}): TripView {
  return {
    id: 't-1',
    name: 'Osaka',
    icon: 'plane',
    hue: 3,
    currencyCode: 'AUD',
    startsOn: null,
    endsOn: null,
    members: [you, bob, cara],
    items: [],
    yourNetMinor: 0,
    groupSpendMinor: 0,
    yourShareMinor: 0,
    youFrontedMinor: 0,
    youAreCreator: true,
    ...overrides,
  }
}

const emptySettlement: SettlementView = { rows: [], yourNetMinor: 0, allSquare: true }

function makeRouter(): Router {
  const stub = { template: '<div />' }
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/signin', name: 'signin', component: stub },
      { path: '/', name: 'trips', component: stub },
      { path: '/trips/:tripId', name: 'trip', component: stub, props: true },
      { path: '/join/:tripId', name: 'join', component: stub, props: true },
    ],
  })
}

let router: Router

beforeEach(() => {
  setActivePinia(createPinia())
  router = makeRouter()
  for (const fn of Object.values(mocked)) fn.mockReset()
})

// Sheets teleport to <body>; stubbing the teleport keeps their content inside the wrapper where
// the assertions can see it.
const global = () => ({ plugins: [router], stubs: { teleport: true } })

describe('SignInScreen', () => {
  it('signs in with the trimmed name and moves on', async () => {
    mocked.signIn!.mockResolvedValue({
      id: 'u1',
      displayName: 'Alice',
      email: 'a@x',
      photoUrl: null,
      friends: [],
    })
    await router.push('/signin')

    const screen = mount(SignInScreen, { global: global() })
    await findByTestId(screen, 'signin-name').setValue('  Alice  ')
    await screen.find('form').trigger('submit')
    await flushPromises()

    expect(mocked.signIn).toHaveBeenCalledWith('Alice')
    expect(router.currentRoute.value.path).toBe('/')
  })
})

describe('SignInScreen', () => {
  it('fast-forwards somebody already signed in, straight to where they were going', async () => {
    mocked.me!.mockResolvedValue({
      id: 'u1',
      displayName: 'Alice',
      email: 'a@x',
      photoUrl: null,
      friends: [],
    })
    await router.push('/signin?next=/trips/t-1')

    mount(SignInScreen, { global: global() })
    await flushPromises()

    expect(mocked.signIn).not.toHaveBeenCalled()
    expect(router.currentRoute.value.fullPath).toBe('/trips/t-1')
  })
})

describe('TripsScreen', () => {
  it('shows every group and the overall position', async () => {
    mocked.me!.mockResolvedValue({
      id: 'u1',
      displayName: 'Alice',
      email: 'a@x',
      photoUrl: null,
      friends: [],
    })
    mocked.trips!.mockResolvedValue({
      trips: [trip({ yourNetMinor: -4_280 }), trip({ id: 't-2', name: 'Flat', yourNetMinor: 0 })],
      overalls: [{ currencyCode: 'AUD', netMinor: -4_280 }],
      settledTripCount: 1,
    })

    const screen = mount(TripsScreen, { global: global() })
    await flushPromises()

    expect(screen.text()).toContain('Osaka')
    expect(screen.text()).toContain('Flat')
    expect(screen.text()).toContain('You owe')
    expect(screen.text()).toContain('42.80')
    expect(screen.text()).toContain('1 of 2 settled')
  })
})

describe('TripScreen', () => {
  function serve(tripView: TripView, settlement: SettlementView = emptySettlement) {
    mocked.trip!.mockResolvedValue(tripView)
    mocked.settlement!.mockResolvedValue(settlement)
    mocked.categories!.mockResolvedValue([
      { id: 'c-food', key: 'food', nameEn: 'Food', nameZh: '餐饮', icon: 'utensils', hue: 1, builtIn: true },
    ])
  }

  it('shows the three headline figures the server derived, and groups the feed by day', async () => {
    serve(
      trip({
        yourNetMinor: 9_000,
        // The three figures come from the payload, derived by the engine — the screen no longer
        // re-sums the items itself (the stale-number risk the design forbids). Deliberately set to
        // values the fixture's items do NOT sum to, so re-summing them would show different numbers
        // and this test would fail — proving the screen reads the payload, not the item list.
        groupSpendMinor: 12_345,
        yourShareMinor: 4_321,
        youFrontedMinor: 8_888,
        items: [
          item({ id: 'i-1', amountMinor: 9_000, yourShareMinor: 3_000, spentOn: '2026-08-07' }),
          item({
            id: 'i-2',
            amountMinor: 3_000,
            yourShareMinor: 1_000,
            spentOn: '2026-08-06',
            payerMemberId: bob.id,
            title: 'Taxi',
          }),
        ],
      }),
    )

    const screen = mount(TripScreen, { props: { tripId: 't-1' }, global: global() })
    await flushPromises()

    expect(screen.text()).toContain('Group spend')
    expect(screen.text()).toContain('123.45') // groupSpendMinor from the payload, not the item sum (120.00)
    expect(screen.text()).toContain('43.21') //  yourShareMinor from the payload, not the item sum (40.00)
    expect(screen.text()).toContain('88.88') //  youFrontedMinor from the payload, not the item sum (90.00)
    // Two different days, two day headers.
    expect(findAllByTestId(screen, 'expense-day')).toHaveLength(2)

    // The feed speaks the signed delta, both ways round: the bill you paid is money coming back
    // (total − your share), the bill Bob paid is your share going out.
    const rows = screen.findAllComponents({ name: 'ExpenseRow' })
    expect(rows[0]!.props('yourShareMinor')).toBe(6_000) // 9000 you paid, minus your 3000 share
    expect(rows[0]!.text()).toContain('you fronted') // you paid → what you fronted for the others
    expect(rows[1]!.props('yourShareMinor')).toBe(-1_000) // Bob paid; your share of 1000 is owed
    expect(rows[1]!.text()).toContain('your share') // Bob paid → your slice of his bill, not a debt
  })

  it('speaks the viewer’s frame on who-owes-who: an API +6000 is “You owe”', async () => {
    serve(trip({ yourNetMinor: -6_000 }), {
      rows: [
        {
          memberId: bob.id,
          displayName: 'Bob',
          personHue: 2,
          owedMinor: 6_000,
          pending: [],
          settled: [],
          rejected: [],
        },
      ],
      yourNetMinor: -6_000,
      allSquare: false,
    })

    const screen = mount(TripScreen, { props: { tripId: 't-1' }, global: global() })
    await flushPromises()

    const row = screen.findComponent({ name: 'BalanceRow' })
    expect(row.props('owedMinor')).toBe(-6_000)
    expect(row.text()).toContain('You owe')
    expect(row.text()).toContain('Pay')
  })

  it('sinks all-square people below real debts in who-owes-who, and fades them', async () => {
    // You're square with Bob but owe Cara. Cara must lead; Bob is kept for reassurance but sunk to
    // the bottom and muted, so a $0 row never sits above money that still needs acting on.
    serve(trip({ yourNetMinor: -6_000 }), {
      rows: [
        {
          memberId: bob.id,
          displayName: 'Bob',
          personHue: 2,
          owedMinor: 0,
          pending: [],
          settled: [],
          rejected: [],
        },
        {
          memberId: cara.id,
          displayName: 'Cara',
          personHue: 3,
          owedMinor: 6_000,
          pending: [],
          settled: [],
          rejected: [],
        },
      ],
      yourNetMinor: -6_000,
      allSquare: false,
    })

    const screen = mount(TripScreen, { props: { tripId: 't-1' }, global: global() })
    await flushPromises()

    const rows = screen.findAllComponents({ name: 'BalanceRow' })
    expect(rows[0]!.props('displayName')).toBe('Cara')
    expect(rows[0]!.props('muted')).toBe(false)
    expect(rows[1]!.props('displayName')).toBe('Bob')
    expect(rows[1]!.props('muted')).toBe(true)
  })

  it('filters the feed to unsettled without touching the data', async () => {
    serve(
      trip({
        items: [
          item({ id: 'i-1', state: 'ALL_SQUARE', title: 'Paid off' }),
          item({ id: 'i-2', title: 'Still open' }),
        ],
      }),
    )
    const screen = mount(TripScreen, { props: { tripId: 't-1' }, global: global() })
    await flushPromises()

    expect(screen.text()).toContain('Paid off')
    await findByTestId(screen, 'filter-unsettled').trigger('click')

    expect(screen.text()).not.toContain('Paid off')
    expect(screen.text()).toContain('Still open')
  })
})

describe('AddExpenseSheet', () => {
  it('previews the exact largest-remainder shares and saves what it showed', async () => {
    // The minted id is the split's salt; pinning it makes every previewed cent derivable.
    const pinned = 'cafebabe-dead-4eef-cafe-babedead4eef'
    vi.spyOn(crypto, 'randomUUID').mockReturnValue(pinned)
    mocked.createItem!.mockResolvedValue(item({}))

    const sheet = mount(AddExpenseSheet, {
      props: {
        open: false,
        trip: trip(),
        categories: [
          {
            id: 'c-food',
            key: 'food',
            nameEn: 'Food',
            nameZh: '餐饮',
            icon: 'utensils',
            hue: 1,
            builtIn: true,
          },
        ],
      },
      global: global(),
    })
    await sheet.setProps({ open: true })
    await nextTick()

    // 100.01 typed on the keypad: 1 0 0 0 1.
    for (const key of ['1', '0', '0', '0', '1']) {
      await findByTestId(sheet, `key-${key}`).trigger('click')
    }
    expect(sheet.text()).toContain('100.01')

    // On to step two; untick Cara, so two people share 10001.
    await findByTestId(sheet, 'next-step').trigger('click')
    const caraRow = findAllByTestId(sheet, 'person-toggle').find((r) => r.text().includes('Cara'))!
    await caraRow.trigger('click')

    const expected = splitShares({ totalMinor: 10_001, weights: [1, 1], salt: saltFor(pinned) })
    expect(expected.reduce((a, b) => a + b, 0)).toBe(10_001)
    for (const share of expected) {
      expect(sheet.text()).toContain((share / 100).toFixed(2))
    }

    await findByTestId(sheet, 'save-expense').trigger('click')
    await flushPromises()

    expect(mocked.createItem).toHaveBeenCalledWith(
      't-1',
      expect.objectContaining({
        id: pinned,
        amountMinor: 10_001,
        splitRule: 'EQUAL',
        sharedBy: [{ memberId: you.id }, { memberId: bob.id }],
      }),
    )
  })
})

describe('AddExpenseSheet custom weights', () => {
  it('saves the dragged ratio normalised, not the drag scale', async () => {
    // Custom mode scales every weight up so the bar can move; the payload divides the greatest
    // common divisor back out — an untouched custom split saves as 1:1:1, never 20:20:20.
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('cafebabe-dead-4eef-cafe-babedead4eef')
    mocked.createItem!.mockResolvedValue(item({}))
    const sheet = mount(AddExpenseSheet, {
      props: {
        open: false,
        trip: trip(),
        categories: [
          {
            id: 'c-food',
            key: 'food',
            nameEn: 'Food',
            nameZh: '餐饮',
            icon: 'utensils',
            hue: 1,
            builtIn: true,
          },
        ],
      },
      global: global(),
    })
    await sheet.setProps({ open: true })
    await nextTick()

    await findByTestId(sheet, 'key-5').trigger('click')
    await findByTestId(sheet, 'next-step').trigger('click')
    await findByTestId(sheet, 'mode-custom').trigger('click')
    await findByTestId(sheet, 'save-expense').trigger('click')
    await flushPromises()

    expect(mocked.createItem).toHaveBeenCalledWith(
      't-1',
      expect.objectContaining({
        splitRule: 'WEIGHTED',
        sharedBy: [
          { memberId: you.id, weight: 1 },
          { memberId: bob.id, weight: 1 },
          { memberId: cara.id, weight: 1 },
        ],
      }),
    )
  })
})

describe('ItemDetailSheet', () => {
  // Bob's claim on a bill `you` fronted: you are the person owed, so the server marks it decidable
  // and undoable by you.
  const pendingClaim = {
    id: 'p-1',
    itemId: 'i-1',
    fromMemberId: bob.id,
    toMemberId: you.id,
    amountMinor: 3_000,
    paidOn: '2026-08-07',
    note: null,
    status: 'PENDING' as const,
    proofObjectName: null,
    rejectReason: null,
    reviewedAt: null,
    viewerCanDecide: true,
    viewerCanUndo: true,
  }

  it('offers approve and reject to the person owed, and approves through the API', async () => {
    mocked.itemDetail!.mockResolvedValue({ ...item({}), paybacks: [pendingClaim] })
    mocked.approvePayback!.mockResolvedValue({ ...pendingClaim, status: 'APPROVED' })

    const sheet = mount(ItemDetailSheet, {
      props: { open: false, itemId: 'i-1', trip: trip(), categories: [] },
      global: global(),
    })
    await sheet.setProps({ open: true })
    await flushPromises()

    const approve = findByTestId(sheet, 'approve')
    expect(approve.exists()).toBe(true)
    await approve.trigger('click')
    await flushPromises()

    expect(mocked.approvePayback).toHaveBeenCalledWith('p-1')
    expect(sheet.emitted('changed')).toBeTruthy()
  })

  it('hides the decision from somebody who is not owed, but lets a claimant withdraw', async () => {
    // Bob's own view of a bill Alice paid: he filed the claim, he can cancel it, he cannot decide it.
    const bobsView = {
      ...item({ payerMemberId: cara.id, yourShareMinor: 3_000 }),
      // You filed this claim (from you), so the server says you cannot decide it — only withdraw it.
      paybacks: [{ ...pendingClaim, fromMemberId: you.id, toMemberId: cara.id, viewerCanDecide: false }],
    }
    mocked.itemDetail!.mockResolvedValue(bobsView)

    const sheet = mount(ItemDetailSheet, {
      props: { open: false, itemId: 'i-1', trip: trip(), categories: [] },
      global: global(),
    })
    await sheet.setProps({ open: true })
    await flushPromises()

    expect(findByTestId(sheet, 'approve').exists()).toBe(false)
    expect(findByTestId(sheet, 'undo-claim').exists()).toBe(true)
  })

  it('asks twice before deleting a bill that carries confirmed repayments', async () => {
    mocked.itemDetail!.mockResolvedValue({
      ...item({}),
      paybacks: [
        { ...pendingClaim, id: 'p-ok', status: 'APPROVED' as const, amountMinor: 2_000 },
        { ...pendingClaim, id: 'p-ok2', status: 'APPROVED' as const, amountMinor: 1_000 },
      ],
    })
    const answers = [true, false] // yes to delete, then cold feet at the money question
    const asked: string[] = []
    vi.stubGlobal('confirm', (message: string) => (asked.push(message), answers.shift() ?? false))

    const sheet = mount(ItemDetailSheet, {
      props: { open: false, itemId: 'i-1', trip: trip(), categories: [] },
      global: global(),
    })
    await sheet.setProps({ open: true })
    await flushPromises()
    await findByTestId(sheet, 'delete-item').trigger('click')
    await flushPromises()

    expect(asked).toHaveLength(2)
    expect(asked[1]).toContain('2') //  both confirmed repayments counted
    expect(asked[1]).toContain('$30.00') // and their sum named
    expect(mocked.deleteItem).not.toHaveBeenCalled()
    vi.unstubAllGlobals()
  })

  it('asks only once when nothing confirmed is at stake', async () => {
    mocked.itemDetail!.mockResolvedValue({ ...item({}), paybacks: [] })
    mocked.deleteItem!.mockResolvedValue(undefined)
    let asks = 0
    vi.stubGlobal('confirm', () => ((asks += 1), true))

    const sheet = mount(ItemDetailSheet, {
      props: { open: false, itemId: 'i-1', trip: trip(), categories: [] },
      global: global(),
    })
    await sheet.setProps({ open: true })
    await flushPromises()
    await findByTestId(sheet, 'delete-item').trigger('click')
    await flushPromises()

    expect(asks).toBe(1)
    expect(mocked.deleteItem).toHaveBeenCalledWith('i-1')
    vi.unstubAllGlobals()
  })
})

describe('EditSplitSheet', () => {
  it('previews the re-divided shares with the existing salt, and saves what it showed', async () => {
    // The hotel case in one sheet: Cara was left off the bill; ticking her on re-divides 9000
    // three ways. The salt is the item's own id — pinned, so every previewed cent is derivable.
    const existing = item({
      id: 'cafebabe-dead-4eef-cafe-babedead4eef',
      splits: [
        { memberId: you.id, amountMinor: 4_500, weight: null, exactAmountMinor: null },
        { memberId: bob.id, amountMinor: 4_500, weight: null, exactAmountMinor: null },
      ],
    })
    mocked.patchItem!.mockResolvedValue(existing)

    const sheet = mount(EditSplitSheet, {
      props: { open: false, trip: trip(), item: null },
      global: global(),
    })
    await sheet.setProps({ open: true, item: existing })
    await nextTick()

    const caraRow = findAllByTestId(sheet, 'person-toggle').find((r) => r.text().includes('Cara'))!
    await caraRow.trigger('click')

    const expected = splitShares({ totalMinor: 9_000, weights: [1, 1, 1], salt: saltFor(existing.id) })
    for (const share of expected) {
      expect(sheet.text()).toContain((share / 100).toFixed(2))
    }

    await findByTestId(sheet, 'save-split').trigger('click')
    await flushPromises()

    expect(mocked.patchItem).toHaveBeenCalledWith(existing.id, {
      amountMinor: 9_000,
      payerMemberId: you.id,
      splitRule: 'EQUAL',
      sharedBy: [{ memberId: you.id }, { memberId: bob.id }, { memberId: cara.id }],
    })
    expect(sheet.emitted('saved')).toBeTruthy()
  })

  it("scales a weighted bill's ratio up for the drag, keeping the ratio itself", async () => {
    // A stored 2:1 arrives at the bar as 40:20 — same split to the cent, but with room to move;
    // at raw small weights every adjacent pair pins the handle where it stands.
    const weighted = item({
      id: 'cafebabe-dead-4eef-cafe-babedead4eef',
      splitRule: 'WEIGHTED',
      splits: [
        { memberId: you.id, amountMinor: 6_000, weight: 2, exactAmountMinor: null },
        { memberId: bob.id, amountMinor: 3_000, weight: 1, exactAmountMinor: null },
      ],
    })
    const sheet = mount(EditSplitSheet, {
      props: { open: false, trip: trip(), item: null },
      global: global(),
    })
    await sheet.setProps({ open: true, item: weighted })
    await nextTick()

    const bar = sheet.findComponent({ name: 'SplitBar' })
    expect(bar.exists()).toBe(true)
    expect((bar.props('people') as { weight: number }[]).map((p) => p.weight)).toEqual([40, 20])
  })

  it('lets a wrong amount be corrected, re-deriving every share live', async () => {
    const existing = item({ id: 'cafebabe-dead-4eef-cafe-babedead4eef' })
    mocked.patchItem!.mockResolvedValue(existing)

    const sheet = mount(EditSplitSheet, {
      props: { open: false, trip: trip(), item: null },
      global: global(),
    })
    await sheet.setProps({ open: true, item: existing })
    await nextTick()

    // Pre-filled with what the bill says now.
    const amount = findByTestId(sheet, 'edit-amount')
    expect(amount.text()).toContain('90.00')

    // Tap the box: the app's own keypad unfolds — no OS keyboard to rely on, least of all on a
    // desktop browser. (Key-by-key plumbing is pinned in AmountKeypadField's own tests; here the
    // corrected amount is driven through the field's v-model contract.)
    await amount.trigger('click')
    const field = sheet.findComponent(AmountKeypadField)
    expect(findByTestId(field, 'key-del').exists()).toBe(true)
    field.vm.$emit('update:modelValue', 12_000)
    await nextTick()
    expect(findByTestId(sheet, 'edit-amount').text()).toContain('120.00')
    const expected = splitShares({
      totalMinor: 12_000,
      weights: [1, 1, 1],
      salt: saltFor(existing.id),
    })
    for (const share of expected) {
      expect(sheet.text()).toContain((share / 100).toFixed(2))
    }

    await findByTestId(sheet, 'save-split').trigger('click')
    await flushPromises()

    expect(mocked.patchItem).toHaveBeenCalledWith(
      existing.id,
      expect.objectContaining({ amountMinor: 12_000 }),
    )
  })
})

describe('InviteSheet', () => {
  it('writes a name onto the roster and reports the change', async () => {
    mocked.addMember!.mockResolvedValue({ ...cara, id: 'm-new', displayName: 'Dana' })
    const sheet = mount(InviteSheet, { props: { open: true, trip: trip() }, global: global() })
    await nextTick()

    await findByTestId(sheet, 'member-name').setValue('Dana')
    await sheet.find('form').trigger('submit')
    await flushPromises()

    expect(mocked.addMember).toHaveBeenCalledWith('t-1', 'Dana')
    expect(sheet.emitted('changed')).toBeTruthy()
  })

  it('hands out a link with the token in the fragment, never the query string', async () => {
    mocked.invite!.mockResolvedValue({ token: 'tok-abc', expiresAt: '2026-09-01T00:00:00Z' })
    const written: string[] = []
    vi.stubGlobal('navigator', {
      ...navigator,
      clipboard: { writeText: (text: string) => (written.push(text), Promise.resolve()) },
    })

    const sheet = mount(InviteSheet, { props: { open: true, trip: trip() }, global: global() })
    await nextTick()
    await findByTestId(sheet, 'copy-link').trigger('click')
    await flushPromises()

    expect(written[0]).toContain('/join/t-1#token=tok-abc')
    expect(written[0]).not.toContain('?token')
    vi.unstubAllGlobals()
  })
})

describe('ClaimPaybackSheet', () => {
  it('pre-fills what is still owed and files the claim against the bill', async () => {
    mocked.submitItemPayback!.mockResolvedValue({})
    const sheet = mount(ClaimPaybackSheet, {
      props: {
        open: false,
        itemId: 'i-1',
        toName: 'Alice',
        prefillMinor: 2_500,
        fromMemberId: bob.id,
        currencyCode: 'AUD',
        symbol: '$',
      },
      global: global(),
    })
    await sheet.setProps({ open: true })
    await nextTick()

    expect(findByTestId(sheet, 'claim-amount').text()).toContain('25.00')

    await sheet.find('form').trigger('submit')
    await flushPromises()

    expect(mocked.submitItemPayback).toHaveBeenCalledWith(
      'i-1',
      expect.objectContaining({ fromMemberId: bob.id, amountMinor: 2_500 }),
    )
    expect(sheet.emitted('saved')).toBeTruthy()
  })
})

describe('JoinScreen', () => {
  it('reads the token from the fragment, shows the free names, and claims the chosen one', async () => {
    mocked.claimable!.mockResolvedValue({
      tripName: 'Osaka',
      members: [{ id: cara.id, displayName: 'Cara', personHue: 3 }],
    })
    mocked.claim!.mockResolvedValue(trip())
    await router.push('/join/t-1#token=tok-abc')

    const screen = mount(JoinScreen, { props: { tripId: 't-1' }, global: global() })
    await flushPromises()

    expect(mocked.claimable).toHaveBeenCalledWith('t-1', 'tok-abc')
    expect(screen.text()).toContain('Cara')

    await findByTestId(screen, 'join-member').trigger('click')
    await findByTestId(screen, 'join-claim').trigger('click')
    await flushPromises()

    expect(mocked.claim).toHaveBeenCalledWith('t-1', 'tok-abc', cara.id)
    expect(router.currentRoute.value.path).toBe('/trips/t-1')
  })
})

describe('SheetPanel', () => {
  it('closes on Escape and on the scrim, and never loses the page behind it', async () => {
    const sheet = mount(SheetPanel, {
      props: { open: true, title: 'A sheet' },
      global: { stubs: { teleport: true } },
    })

    await sheet.find('.sheet__scrim').trigger('click')
    expect(sheet.emitted('close')).toHaveLength(1)

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(sheet.emitted('close')).toHaveLength(2)
  })
})

describe('SettleUpSheet', () => {
  it('pre-fills Pay with exactly what is owed and files it as a settlement', async () => {
    mocked.submitSettlement!.mockResolvedValue({})
    const sheet = mount(SettleUpSheet, {
      props: {
        open: true,
        tripId: 't-1',
        myMemberId: you.id,
        youAreCreator: true,
        rows: [
          {
            memberId: bob.id,
            displayName: 'Bob',
            personHue: 2,
            owedMinor: 6_000,
            pending: [],
            settled: [],
            rejected: [],
          },
        ],
        currencyCode: 'AUD',
        symbol: '$',
      },
      global: global(),
    })
    await nextTick()

    await findByTestId(sheet, 'row-pay').trigger('click')
    expect(findByTestId(sheet, 'pay-amount').text()).toContain('60.00')

    await sheet.find('form').trigger('submit')
    await flushPromises()

    expect(mocked.submitSettlement).toHaveBeenCalledWith('t-1', { toMemberId: bob.id, amountMinor: 6_000 })
    expect(sheet.emitted('changed')).toBeTruthy()
  })

  it('reminds somebody who owes you, and nothing more', async () => {
    mocked.remind!.mockResolvedValue(undefined)
    const sheet = mount(SettleUpSheet, {
      props: {
        open: true,
        tripId: 't-1',
        myMemberId: you.id,
        youAreCreator: true,
        rows: [
          {
            memberId: bob.id,
            displayName: 'Bob',
            personHue: 2,
            owedMinor: -4_230,
            pending: [],
            settled: [],
            rejected: [],
          },
        ],
        currencyCode: 'AUD',
        symbol: '$',
      },
      global: global(),
    })
    await nextTick()

    await findByTestId(sheet, 'row-remind').trigger('click')
    await flushPromises()

    expect(mocked.remind).toHaveBeenCalledWith('t-1', bob.id)
    expect(mocked.submitSettlement).not.toHaveBeenCalled()
  })

  const declined = (over: Partial<PaybackView> = {}): PaybackView => ({
    id: 'p-declined',
    itemId: null,
    fromMemberId: you.id,
    toMemberId: bob.id,
    amountMinor: 12_500,
    paidOn: '2026-08-13',
    note: null,
    status: 'REJECTED',
    proofObjectName: null,
    rejectReason: 'not received',
    reviewedAt: '2026-08-13T02:00:00Z',
    viewerCanDecide: false,
    viewerCanUndo: false,
    ...over,
  })

  function settleRow(over: Partial<SettlementRow> = {}): SettlementRow {
    return {
      memberId: bob.id,
      displayName: 'Bob',
      personHue: 2,
      owedMinor: 12_500,
      pending: [],
      settled: [],
      rejected: [],
      ...over,
    }
  }

  function mountSettle(rows: SettlementRow[]) {
    return mount(SettleUpSheet, {
      props: {
        open: true,
        tripId: 't-1',
        myMemberId: you.id,
        youAreCreator: false,
        rows,
        currencyCode: 'AUD',
        symbol: '$',
      },
      global: global(),
    })
  }

  it('surfaces a declined settlement back to the claimant, with the reason and amount', async () => {
    // The gap this closes: a trip-level settlement has no bill sheet, so a decline used to be
    // filtered out of the payload and vanish — the claimant never learned it was turned down or why.
    const sheet = mountSettle([settleRow({ rejected: [declined()] })])
    await nextTick()

    const strip = findByTestId(sheet, 'declined-claim')
    expect(strip.text()).toContain('Bob declined your payment')
    expect(strip.text()).toContain('not received')
    expect(strip.text()).toContain('125.00')
  })

  it('drops the decline note once a fresh claim to the same person is pending', async () => {
    const pending = declined({ id: 'p-pending', status: 'PENDING', rejectReason: null })
    const sheet = mountSettle([settleRow({ pending: [pending], rejected: [declined()] })])
    await nextTick()

    // Retrying speaks for itself — the old decline should not sit beside the new pending claim.
    expect(findAllByTestId(sheet, 'declined-claim')).toHaveLength(0)
    expect(findByTestId(sheet, 'pending-claim')).toBeTruthy()
  })
})
