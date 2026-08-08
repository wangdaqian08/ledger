import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import AddExpenseSheet from '../src/screens/sheets/AddExpenseSheet.vue'
import ItemDetailSheet from '../src/screens/sheets/ItemDetailSheet.vue'
import SettleUpSheet from '../src/screens/sheets/SettleUpSheet.vue'
import SignInScreen from '../src/screens/SignInScreen.vue'
import TripScreen from '../src/screens/TripScreen.vue'
import TripsScreen from '../src/screens/TripsScreen.vue'
import { i18n } from '../src/i18n'
import { saltFor, splitShares } from '../src/lib/split'
import type { ItemView, MemberView, SettlementView, TripView } from '../src/lib/api'

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
const global = () => ({ plugins: [i18n, router], stubs: { teleport: true } })

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
    await screen.find('input').setValue('  Alice  ')
    await screen.find('form').trigger('submit')
    await flushPromises()

    expect(mocked.signIn).toHaveBeenCalledWith('Alice')
    expect(router.currentRoute.value.path).toBe('/')
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
      overallNetMinor: -4_280,
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

  it('derives the three stats from the items, and groups the feed by day', async () => {
    serve(
      trip({
        yourNetMinor: 9_000,
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
    expect(screen.text()).toContain('120.00') // 9000 + 3000
    expect(screen.text()).toContain('40.00') //  your share 3000 + 1000
    expect(screen.text()).toContain('90.00') //  you fronted item i-1 only
    // Two different days, two day headers.
    expect(screen.findAll('.trip__day')).toHaveLength(2)

    // The feed speaks the signed delta, both ways round: the bill you paid is money coming back
    // (total − your share), the bill Bob paid is your share going out.
    const rows = screen.findAllComponents({ name: 'ExpenseRow' })
    expect(rows[0]!.props('yourShareMinor')).toBe(6_000) // 9000 you paid, minus your 3000 share
    expect(rows[0]!.text()).toContain('you get')
    expect(rows[1]!.props('yourShareMinor')).toBe(-1_000) // Bob paid; your share of 1000 is owed
    expect(rows[1]!.text()).toContain('you owe')
  })

  it('speaks the viewer’s frame on who-owes-who: an API +6000 is “You owe”', async () => {
    serve(trip({ yourNetMinor: -6_000 }), {
      rows: [{ memberId: bob.id, displayName: 'Bob', personHue: 2, owedMinor: 6_000, pending: [] }],
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
    await screen.findAll('.trip__filter')[1]!.trigger('click')

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
      const button = sheet.findAll('button').find((b) => b.text() === key)!
      await button.trigger('click')
    }
    expect(sheet.text()).toContain('100.01')

    // On to step two; untick Cara, so two people share 10001.
    await sheet
      .findAll('button')
      .find((b) => b.text() === 'Next')!
      .trigger('click')
    const caraRow = sheet.findAll('.row').find((r) => r.text().includes('Cara'))!
    await caraRow.trigger('click')

    const expected = splitShares({ totalMinor: 10_001, weights: [1, 1], salt: saltFor(pinned) })
    expect(expected.reduce((a, b) => a + b, 0)).toBe(10_001)
    for (const share of expected) {
      expect(sheet.text()).toContain((share / 100).toFixed(2))
    }

    await sheet
      .findAll('button')
      .find((b) => b.text() === 'Save expense')!
      .trigger('click')
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

describe('ItemDetailSheet', () => {
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

    const approve = sheet.findAll('button').find((b) => b.text() === 'Yes, paid me')
    expect(approve).toBeTruthy()
    await approve!.trigger('click')
    await flushPromises()

    expect(mocked.approvePayback).toHaveBeenCalledWith('p-1')
    expect(sheet.emitted('changed')).toBeTruthy()
  })

  it('hides the decision from somebody who is not owed, but lets a claimant withdraw', async () => {
    // Bob's own view of a bill Alice paid: he filed the claim, he can cancel it, he cannot decide it.
    const bobsView = {
      ...item({ payerMemberId: cara.id, yourShareMinor: 3_000 }),
      paybacks: [{ ...pendingClaim, fromMemberId: you.id, toMemberId: cara.id }],
    }
    mocked.itemDetail!.mockResolvedValue(bobsView)

    const sheet = mount(ItemDetailSheet, {
      props: { open: false, itemId: 'i-1', trip: trip(), categories: [] },
      global: global(),
    })
    await sheet.setProps({ open: true })
    await flushPromises()

    expect(sheet.findAll('button').find((b) => b.text() === 'Yes, paid me')).toBeUndefined()
    expect(sheet.findAll('button').find((b) => b.text() === 'Cancel')).toBeTruthy()
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
        rows: [{ memberId: bob.id, displayName: 'Bob', personHue: 2, owedMinor: 6_000, pending: [] }],
        currencyCode: 'AUD',
        symbol: '$',
      },
      global: global(),
    })
    await nextTick()

    await sheet
      .findAll('button')
      .find((b) => b.text() === 'Pay')!
      .trigger('click')
    const input = sheet.find('input')
    expect((input.element as HTMLInputElement).value).toBe('60.00')

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
        rows: [{ memberId: bob.id, displayName: 'Bob', personHue: 2, owedMinor: -4_230, pending: [] }],
        currencyCode: 'AUD',
        symbol: '$',
      },
      global: global(),
    })
    await nextTick()

    await sheet
      .findAll('button')
      .find((b) => b.text() === 'Remind')!
      .trigger('click')
    await flushPromises()

    expect(mocked.remind).toHaveBeenCalledWith('t-1', bob.id)
    expect(mocked.submitSettlement).not.toHaveBeenCalled()
  })
})
