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
import { findAllByTestId, findByTestId, testId } from './testids'
import en from '../src/i18n/en'
import { useSession } from '@/stores/session'
import { saltFor, splitShares } from '@/lib/split'
import type {
  FamiliesView,
  FamilyMemberView,
  ItemView,
  MemberView,
  PaybackView,
  SettlementRow,
  SettlementView,
  TripsView,
  TripView,
} from '@/lib/api'
import TallyButton from '@/components/TallyButton.vue'

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
      renameMember: vi.fn(),
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
      previewFamilies: vi.fn(),
      closeTrip: vi.fn(),
      reopenTrip: vi.fn(),
      hideTrip: vi.fn(),
      unhideTrip: vi.fn(),
      deleteTrip: vi.fn(),
      restoreTrip: vi.fn(),
      uploadReceipt: vi.fn(),
      deleteReceipt: vi.fn(),
    },
  }
})

const { api } = await import('../src/lib/api')
const mocked = api as unknown as Record<string, ReturnType<typeof vi.fn>>

// ---- Fixtures: one small trip, stated once. ----

const you: MemberView = { id: 'm-you', displayName: 'Alice', personHue: 1, claimed: true, isYou: true }
const bob: MemberView = { id: 'm-bob', displayName: 'Bob', personHue: 2, claimed: true, isYou: false }
const cara: MemberView = { id: 'm-cara', displayName: 'Cara', personHue: 3, claimed: false, isYou: false }

const foodCategories = [
  { id: 'c-food', key: 'food', nameEn: 'Food', nameZh: '餐饮', icon: 'utensils', hue: 1, builtIn: true },
]

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
    receipt: null,
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
    closedAt: null,
    hiddenAt: null,
    unsettledMinor: 0,
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

describe('SignInScreen version footer', () => {
  it("shows 'dev' when no build-time version is baked in", async () => {
    await router.push('/signin')

    const screen = mount(SignInScreen, { global: global() })
    await flushPromises()

    expect(findByTestId(screen, 'app-version').text()).toBe('dev')
  })

  it('shows the tagged release version baked in at build time', async () => {
    vi.stubEnv('VITE_APP_VERSION', 'v0.3.1')
    await router.push('/signin')

    const screen = mount(SignInScreen, { global: global() })
    await flushPromises()

    expect(findByTestId(screen, 'app-version').text()).toBe('v0.3.1')
    vi.unstubAllEnvs()
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

  /** Signed in, with whatever the home payload should be. */
  async function home(overview: Partial<TripsView> & Pick<TripsView, 'trips'>) {
    mocked.me!.mockResolvedValue({
      id: 'u1',
      displayName: 'Alice',
      email: 'a@x',
      photoUrl: null,
      friends: [],
    })
    mocked.trips!.mockResolvedValue({ overalls: [], settledTripCount: 0, ...overview })
    const screen = mount(TripsScreen, { global: global() })
    await flushPromises()
    return screen
  }

  it('sorts ended groups into their own section, leaving the live ones above', async () => {
    const screen = await home({
      trips: [
        trip({ id: 't-live', name: 'Osaka' }),
        trip({ id: 't-done', name: 'Hokkaido', closedAt: '2026-08-01T00:00:00Z' }),
      ],
    })

    const completed = findByTestId(screen, 'completed-section')
    expect(completed.exists()).toBe(true)
    expect(completed.text()).toContain('Hokkaido')
    expect(completed.text()).not.toContain('Osaka')
  })

  it('keeps put-away trips off the list until somebody asks for them', async () => {
    const screen = await home({
      trips: [
        trip({ id: 't-done', name: 'Hokkaido', closedAt: '2026-08-01T00:00:00Z' }),
        trip({
          id: 't-away',
          name: 'Old Ski Trip',
          closedAt: '2026-07-01T00:00:00Z',
          hiddenAt: '2026-07-02T00:00:00Z',
        }),
      ],
    })

    expect(screen.text()).not.toContain('Old Ski Trip')
    const toggle = findByTestId(screen, 'toggle-hidden')
    // The count is the point: it says how much is behind the toggle without opening it.
    expect(toggle.text()).toContain('1')

    await toggle.trigger('click')
    expect(screen.text()).toContain('Old Ski Trip')
    // Revealing is not un-hiding — the card stays marked as put away.
    const away = findAllByTestId(screen, 'group-card').find((card) => card.text().includes('Old Ski Trip'))!
    expect(away.classes()).toContain('trips__card--away')

    await toggle.trigger('click')
    expect(screen.text()).not.toContain('Old Ski Trip')
  })

  it('offers no toggle at all when nothing has been put away', async () => {
    const screen = await home({ trips: [trip({ id: 't-done', closedAt: '2026-08-01T00:00:00Z' })] })

    expect(findByTestId(screen, 'completed-section').exists()).toBe(true)
    expect(findByTestId(screen, 'toggle-hidden').exists()).toBe(false)
  })

  it('says so, quietly, when every group is finished', async () => {
    const screen = await home({ trips: [trip({ id: 't-done', closedAt: '2026-08-01T00:00:00Z' })] })
    expect(findByTestId(screen, 'live-empty').exists()).toBe(true)

    const withLive = await home({ trips: [trip()] })
    expect(findByTestId(withLive, 'live-empty').exists()).toBe(false)
  })

  it('shows what you deleted with the date it stops being restorable, and puts it back', async () => {
    mocked.restoreTrip!.mockResolvedValue(trip())
    const screen = await home({
      trips: [],
      deleted: [
        {
          id: 't-gone',
          name: 'Snow Trip',
          icon: 'plane',
          hue: 2,
          deletedAt: '2026-08-18T00:00:00Z',
          purgesAt: '2026-09-17T00:00:00Z',
        },
      ],
    })

    const row = findByTestId(screen, 'deleted-trip')
    expect(row.text()).toContain('Snow Trip')
    // The deadline is spelled out rather than left as "30 days" for the reader to count.
    expect(row.text()).toContain('Restorable until')
    expect(row.text()).toContain('Sep 17, 2026')

    await findByTestId(screen, 'restore-trip').trigger('click')
    await flushPromises()

    expect(mocked.restoreTrip).toHaveBeenCalledWith('t-gone')
    // Re-fetched rather than patched in place: every figure on this screen is the engine's.
    expect(mocked.trips).toHaveBeenCalledTimes(2)
  })

  it('has no Recently deleted section for somebody who has deleted nothing', async () => {
    const screen = await home({ trips: [trip()] })
    expect(findByTestId(screen, 'deleted-section').exists()).toBe(false)
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

  it('offers the expense export as a plain download of this trip', async () => {
    serve(trip())
    const screen = mount(TripScreen, { props: { tripId: 't-1' }, global: global() })
    await flushPromises()

    const link = findByTestId(screen, 'export-csv')
    // A real anchor with a download attribute — the browser fetches it with the session cookie;
    // no JS in the path that could round-trip the numbers through floats.
    expect(link.element.tagName).toBe('A')
    expect(link.attributes('href')).toBe('/api/trips/t-1/expenses.csv')
    expect(link.attributes('download')).toBeDefined()
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

    // On to step two; nobody is ticked by default now (spec §3), so tick You and Bob — two people
    // share 10001, Cara left off.
    await findByTestId(sheet, 'next-step').trigger('click')
    for (const name of ['You', 'Bob']) {
      const row = findAllByTestId(sheet, 'person-toggle').find((r) => r.text().includes(name))!
      await row.trigger('click')
    }

    const expected = splitShares({ totalMinor: 10_001, weights: [1, 1], salt: saltFor(pinned) })
    expect(expected.reduce((a, b) => a + b, 0)).toBe(10_001)
    for (const share of expected) {
      expect(sheet.text()).toContain((share / 100).toFixed(2))
    }

    // The comment is folded away until it is wanted — an optional afterthought like the photo.
    expect(findByTestId(sheet, 'comment-input').exists()).toBe(false)
    await findByTestId(sheet, 'comment-toggle').trigger('click')
    await findByTestId(sheet, 'comment-input').setValue('  Cara sat this one out  ')

    await findByTestId(sheet, 'save-expense').trigger('click')
    await flushPromises()

    expect(mocked.createItem).toHaveBeenCalledWith(
      't-1',
      expect.objectContaining({
        id: pinned,
        amountMinor: 10_001,
        splitRule: 'EQUAL',
        sharedBy: [{ memberId: you.id }, { memberId: bob.id }],
        note: 'Cara sat this one out',
      }),
    )
  })

  it('refuses to save a comment past the limit, and never carries one into the next expense', async () => {
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('cafebabe-dead-4eef-cafe-babedead4eef')
    mocked.createItem!.mockResolvedValue(item({}))
    const sheet = mount(AddExpenseSheet, {
      props: { open: false, trip: trip(), categories: foodCategories },
      global: global(),
    })
    await sheet.setProps({ open: true })
    await nextTick()

    await findByTestId(sheet, 'key-5').trigger('click')
    await findByTestId(sheet, 'next-step').trigger('click')
    await findByTestId(sheet, 'split-all').trigger('click')
    await findByTestId(sheet, 'comment-toggle').trigger('click')

    const tooMany = Array.from({ length: 101 }, (_, i) => `w${i}`).join(' ')
    await findByTestId(sheet, 'comment-input').setValue(tooMany)
    expect(findByTestId(sheet, 'save-expense').attributes('disabled')).toBeDefined()

    // Back under the limit and the save is offered again.
    await findByTestId(sheet, 'comment-input').setValue('short enough')
    expect(findByTestId(sheet, 'save-expense').attributes('disabled')).toBeUndefined()

    // A fresh sheet is a fresh expense: the last comment must not ride along on the next bill.
    await sheet.setProps({ open: false })
    await sheet.setProps({ open: true })
    await nextTick()
    await findByTestId(sheet, 'key-5').trigger('click')
    await findByTestId(sheet, 'next-step').trigger('click')
    expect(findByTestId(sheet, 'comment-input').exists()).toBe(false)
    await findByTestId(sheet, 'comment-toggle').trigger('click')
    expect((findByTestId(sheet, 'comment-input').element as HTMLTextAreaElement).value).toBe('')
  })

  it('refuses an over-long comment in the save itself, not only on the button', async () => {
    // A disabled attribute is a courtesy to whoever is looking, not a rule. Reach the handler the
    // way a future Enter-to-submit would and it still has to refuse, or the server does it for us.
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('cafebabe-dead-4eef-cafe-babedead4eef')
    mocked.createItem!.mockResolvedValue(item({}))
    const sheet = mount(AddExpenseSheet, {
      props: { open: false, trip: trip(), categories: foodCategories },
      global: global(),
    })
    await sheet.setProps({ open: true })
    await nextTick()

    await findByTestId(sheet, 'key-5').trigger('click')
    await findByTestId(sheet, 'next-step').trigger('click')
    await findByTestId(sheet, 'split-all').trigger('click')
    await findByTestId(sheet, 'comment-toggle').trigger('click')
    await findByTestId(sheet, 'comment-input').setValue(
      Array.from({ length: 101 }, (_, i) => `w${i}`).join(' '),
    )

    const save = sheet
      .findAllComponents(TallyButton)
      .find((button) => button.attributes('data-testid') === 'save-expense')!
    save.vm.$emit('click')
    await flushPromises()

    expect(mocked.createItem).not.toHaveBeenCalled()
  })

  it('counts a typed comment as work worth asking about before the sheet is dismissed', async () => {
    // Everything else that makes the sheet dirty is walked back — the amount deleted to nothing,
    // the title never typed, no photo, back on step 1 — leaving the comment as the only thing a
    // dismissal would destroy. It still has to be asked about.
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('cafebabe-dead-4eef-cafe-babedead4eef')
    const asked: string[] = []
    vi.stubGlobal('confirm', (message: string) => (asked.push(message), false))

    const sheet = mount(AddExpenseSheet, {
      props: { open: false, trip: trip(), categories: foodCategories },
      global: global(),
    })
    await sheet.setProps({ open: true })
    await nextTick()

    await findByTestId(sheet, 'key-5').trigger('click')
    await findByTestId(sheet, 'next-step').trigger('click')
    await findByTestId(sheet, 'comment-toggle').trigger('click')
    await findByTestId(sheet, 'comment-input').setValue('Cara sat this one out')
    await findByTestId(sheet, 'back-step').trigger('click')
    await findByTestId(sheet, 'key-del').trigger('click')

    await findByTestId(sheet, 'sheet-close').trigger('click')

    expect(asked).toEqual([en.addExpense.discardConfirm])
    expect(sheet.emitted('close')).toBeUndefined()
    vi.unstubAllGlobals()
  })

  it('keeps the comment unfolded across a step back and forward', async () => {
    // The disclosure is the sheet's state, not the field's private business: stepping back
    // rebuilds this half of the screen, and a comment already being written must not come back
    // hidden behind a folded row.
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('cafebabe-dead-4eef-cafe-babedead4eef')
    const sheet = mount(AddExpenseSheet, {
      props: { open: false, trip: trip(), categories: foodCategories },
      global: global(),
    })
    await sheet.setProps({ open: true })
    await nextTick()

    await findByTestId(sheet, 'key-5').trigger('click')
    await findByTestId(sheet, 'next-step').trigger('click')
    await findByTestId(sheet, 'comment-toggle').trigger('click')
    await findByTestId(sheet, 'comment-input').setValue('Cara sat this one out')

    await findByTestId(sheet, 'back-step').trigger('click')
    await findByTestId(sheet, 'next-step').trigger('click')

    expect((findByTestId(sheet, 'comment-input').element as HTMLTextAreaElement).value).toBe(
      'Cara sat this one out',
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
    // Nobody ticked by default now; the All chip puts everyone on the bill for the 1:1:1 case.
    await findByTestId(sheet, 'split-all').trigger('click')
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

describe('ItemDetailSheet comment', () => {
  async function openSheet(tripView: TripView = trip()) {
    const sheet = mount(ItemDetailSheet, {
      props: { open: false, itemId: 'i-1', trip: tripView, categories: [] },
      global: global(),
    })
    await sheet.setProps({ open: true })
    await flushPromises()
    return sheet
  }

  it('lets the payer rewrite the comment, and shows the new words without a reload', async () => {
    mocked.itemDetail!.mockResolvedValueOnce({ ...item({ note: 'Cara sat this one out' }), paybacks: [] })
    mocked.patchItem!.mockResolvedValue(item({ note: 'Cara joined after all' }))
    mocked.itemDetail!.mockResolvedValue({ ...item({ note: 'Cara joined after all' }), paybacks: [] })

    const sheet = await openSheet()
    expect(sheet.text()).toContain('Cara sat this one out')

    // The comment itself is the way in — no separate pencil to hunt for.
    await findByTestId(sheet, 'comment-edit').trigger('click')
    expect((findByTestId(sheet, 'comment-input').element as HTMLTextAreaElement).value).toBe(
      'Cara sat this one out',
    )

    await findByTestId(sheet, 'comment-input').setValue('Cara joined after all')
    await findByTestId(sheet, 'comment-save').trigger('click')
    await flushPromises()

    expect(mocked.patchItem).toHaveBeenCalledWith('i-1', { note: 'Cara joined after all' })
    expect(sheet.text()).toContain('Cara joined after all')
    expect(findByTestId(sheet, 'comment-input').exists()).toBe(false)
  })

  it('lets a bill with no comment yet be given one', async () => {
    mocked.itemDetail!.mockResolvedValueOnce({ ...item({ note: null }), paybacks: [] })
    mocked.patchItem!.mockResolvedValue(item({ note: 'Split the taxi too' }))
    mocked.itemDetail!.mockResolvedValue({ ...item({ note: 'Split the taxi too' }), paybacks: [] })

    const sheet = await openSheet()
    // Folded away, exactly as on the add screen — a comment is never the reason this sheet is open.
    expect(findByTestId(sheet, 'comment-input').exists()).toBe(false)
    await findByTestId(sheet, 'comment-toggle').trigger('click')
    await findByTestId(sheet, 'comment-input').setValue('Split the taxi too')
    await findByTestId(sheet, 'comment-save').trigger('click')
    await flushPromises()

    expect(mocked.patchItem).toHaveBeenCalledWith('i-1', { note: 'Split the taxi too' })
  })

  it('gives a bystander the comment to read and no way to touch it', async () => {
    mocked.itemDetail!.mockResolvedValue({
      ...item({ payerMemberId: bob.id, note: 'Cara sat this one out' }),
      paybacks: [],
    })
    const sheet = await openSheet(trip({ youAreCreator: false }))

    expect(sheet.text()).toContain('Cara sat this one out')
    expect(findAllByTestId(sheet, 'comment-edit')).toHaveLength(0)
    expect(findAllByTestId(sheet, 'comment-toggle')).toHaveLength(0)
  })

  it('shows nothing at all to a bystander when there is no comment', async () => {
    mocked.itemDetail!.mockResolvedValue({ ...item({ payerMemberId: bob.id, note: null }), paybacks: [] })
    const sheet = await openSheet(trip({ youAreCreator: false }))

    expect(findAllByTestId(sheet, 'comment-edit')).toHaveLength(0)
    expect(findAllByTestId(sheet, 'comment-toggle')).toHaveLength(0)
  })

  it('offers no comment editing once the trip has ended', async () => {
    // The spending record is closed (the server answers 409); the comment is part of it, so it
    // stays readable and stops being writable, exactly like the receipt.
    mocked.itemDetail!.mockResolvedValue({ ...item({ note: 'Cara sat this one out' }), paybacks: [] })
    const sheet = await openSheet(trip({ closedAt: '2026-08-18T03:00:00Z' }))

    expect(sheet.text()).toContain('Cara sat this one out')
    expect(findAllByTestId(sheet, 'comment-edit')).toHaveLength(0)
    expect(findAllByTestId(sheet, 'comment-toggle')).toHaveLength(0)
  })

  it('separates discarding an edit from deleting the comment outright', async () => {
    mocked.itemDetail!.mockResolvedValue({ ...item({ note: 'Cara sat this one out' }), paybacks: [] })
    mocked.patchItem!.mockResolvedValue(item({ note: null }))
    const asked: string[] = []
    // The global stub answers yes to everything, which would walk straight through the deletion
    // question without ever proving it was put. Capture it, and answer no the first time.
    const answers = [false, true]
    vi.stubGlobal('confirm', (message: string) => (asked.push(message), answers.shift() ?? true))

    const sheet = await openSheet()

    // Emptying the box and backing out changes nothing at all, and asks nothing either.
    await findByTestId(sheet, 'comment-edit').trigger('click')
    await findByTestId(sheet, 'comment-input').setValue('')
    await findByTestId(sheet, 'comment-discard').trigger('click')
    await flushPromises()
    expect(asked).toHaveLength(0)
    expect(mocked.patchItem).not.toHaveBeenCalled()
    expect(sheet.text()).toContain('Cara sat this one out')

    // Emptying it and saving is a deletion, so it is asked about — and answering no does nothing.
    await findByTestId(sheet, 'comment-edit').trigger('click')
    await findByTestId(sheet, 'comment-input').setValue('')
    await findByTestId(sheet, 'comment-save').trigger('click')
    await flushPromises()
    expect(asked).toEqual([en.comment.removeConfirm])
    expect(mocked.patchItem).not.toHaveBeenCalled()
    // Cold feet costs nothing: the editor is still up, and backing out restores the words.
    expect(findByTestId(sheet, 'comment-input').exists()).toBe(true)
    await findByTestId(sheet, 'comment-discard').trigger('click')
    expect(sheet.text()).toContain('Cara sat this one out')

    // Saying yes is how a comment is removed — the server maps "" to null.
    await findByTestId(sheet, 'comment-edit').trigger('click')
    await findByTestId(sheet, 'comment-input').setValue('')
    await findByTestId(sheet, 'comment-save').trigger('click')
    await flushPromises()
    expect(asked).toHaveLength(2)
    expect(mocked.patchItem).toHaveBeenCalledWith('i-1', { note: '' })
    vi.unstubAllGlobals()
  })

  it('folds the whole editor away together, never a Save over a box that is gone', async () => {
    // One disclosure, one owner. Collapsing the row used to hide only the textarea, leaving Save
    // and Discard over an invisible draft, the existing comment gone from the screen, and the row
    // labelled as though there were no comment at all.
    mocked.itemDetail!.mockResolvedValue({ ...item({ note: 'Cara sat this one out' }), paybacks: [] })
    const sheet = await openSheet()

    await findByTestId(sheet, 'comment-edit').trigger('click')
    expect(findByTestId(sheet, 'comment-input').exists()).toBe(true)

    await findByTestId(sheet, 'comment-toggle').trigger('click')
    await nextTick()

    expect(findAllByTestId(sheet, 'comment-input')).toHaveLength(0)
    expect(findAllByTestId(sheet, 'comment-save')).toHaveLength(0)
    expect(findAllByTestId(sheet, 'comment-discard')).toHaveLength(0)
    // Back to the paragraph it came from, words and all.
    expect(sheet.text()).toContain('Cara sat this one out')
    expect(findByTestId(sheet, 'comment-edit').exists()).toBe(true)
  })

  it('never carries a half-typed comment from one bill to the next', async () => {
    mocked.itemDetail!.mockResolvedValue({ ...item({ note: null }), paybacks: [] })
    const sheet = await openSheet()

    await findByTestId(sheet, 'comment-toggle').trigger('click')
    await findByTestId(sheet, 'comment-input').setValue('half a thought')

    mocked.itemDetail!.mockResolvedValue({ ...item({ id: 'i-2', note: null }), paybacks: [] })
    await sheet.setProps({ itemId: 'i-2' })
    await flushPromises()

    // A different bill, folded shut, with nothing of the last one in it.
    expect(findAllByTestId(sheet, 'comment-input')).toHaveLength(0)
    await findByTestId(sheet, 'comment-toggle').trigger('click')
    expect((findByTestId(sheet, 'comment-input').element as HTMLTextAreaElement).value).toBe('')
  })

  it('keeps the words on screen when the save fails, rather than making them be retyped', async () => {
    mocked.itemDetail!.mockResolvedValue({ ...item({ note: 'Cara sat this one out' }), paybacks: [] })
    mocked.patchItem!.mockRejectedValue(new Error('Network unreachable'))

    const sheet = await openSheet()
    await findByTestId(sheet, 'comment-edit').trigger('click')
    await findByTestId(sheet, 'comment-input').setValue('Cara joined after all')
    await findByTestId(sheet, 'comment-save').trigger('click')
    await flushPromises()

    expect(sheet.text()).toContain('Network unreachable')
    expect((findByTestId(sheet, 'comment-input').element as HTMLTextAreaElement).value).toBe(
      'Cara joined after all',
    )
  })

  it('says what tapping the comment does, not only what the comment says', async () => {
    // The paragraph is the edit button; without a name of its own a screen reader announces the
    // comment's text followed by "button" and leaves what it would do to guesswork.
    mocked.itemDetail!.mockResolvedValue({ ...item({ note: 'Cara sat this one out' }), paybacks: [] })
    const sheet = await openSheet()

    expect(findByTestId(sheet, 'comment-edit').attributes('aria-label')).toBe(en.comment.edit)
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
      props: { open: false, trip: trip(), item: null, categories: foodCategories },
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
      title: 'Dinner',
      categoryId: 'c-food',
      spentOn: '2026-08-07',
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
      props: { open: false, trip: trip(), item: null, categories: foodCategories },
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
      props: { open: false, trip: trip(), item: null, categories: foodCategories },
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

  it('lets the creator fix a name in place, and reports the change', async () => {
    mocked.renameMember!.mockResolvedValue({ ...bob, displayName: 'Robert' })
    const sheet = mount(InviteSheet, { props: { open: true, trip: trip() }, global: global() })
    await nextTick()

    await findAllByTestId(sheet, 'rename-member')[1]!.trigger('click')
    const field = findByTestId(sheet, 'rename-name')
    expect((field.element as HTMLInputElement).value).toBe('Bob')

    await field.setValue('Robert')
    await findByTestId(sheet, 'rename-form').trigger('submit')
    await flushPromises()

    expect(mocked.renameMember).toHaveBeenCalledWith('t-1', bob.id, 'Robert')
    expect(sheet.emitted('changed')).toBeTruthy()
  })

  it('offers no rename to a plain member, whose roster is read-only', async () => {
    const sheet = mount(InviteSheet, {
      props: { open: true, trip: trip({ youAreCreator: false }) },
      global: global(),
    })
    await nextTick()

    expect(findAllByTestId(sheet, 'rename-member')).toHaveLength(0)
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
      you: null,
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

  it('tells somebody already aboard which seat is theirs and offers the trip, never the list', async () => {
    mocked.claimable!.mockResolvedValue({
      tripName: 'Osaka',
      you: { id: 'm-you', displayName: 'Jack', personHue: 2 },
      members: [],
    })
    await router.push('/join/t-1#token=tok-abc')

    const screen = mount(JoinScreen, { props: { tripId: 't-1' }, global: global() })
    await flushPromises()

    expect(findByTestId(screen, 'join-already').text()).toContain('Jack')
    expect(findAllByTestId(screen, 'join-member')).toHaveLength(0)
    expect(findAllByTestId(screen, 'join-claim')).toHaveLength(0)
    // Not the all-claimed dead end either — being aboard is its own state, not "no names left".
    expect(screen.text()).not.toContain(en.join.allClaimed)

    await findByTestId(screen, 'join-open').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/trips/t-1')
  })

  it('names the signed-in account, and sign-out routes back here for the right person', async () => {
    mocked.claimable!.mockResolvedValue({
      tripName: 'Osaka',
      you: null,
      members: [{ id: cara.id, displayName: 'Cara', personHue: 3 }],
    })
    const session = useSession()
    session.me = { id: 'u-1', displayName: 'jack', email: 'jack@ledger.test', photoUrl: null, friends: [] }
    session.checked = true
    await router.push('/join/t-1#token=tok-abc')

    const screen = mount(JoinScreen, { props: { tripId: 't-1' }, global: global() })
    await flushPromises()

    expect(findByTestId(screen, 'join-who').text()).toContain('jack')

    await findByTestId(screen, 'join-signout').trigger('click')
    await flushPromises()

    expect(mocked.signOut).toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/signin')
    // The whole invite URL, fragment included — sign-in hands the next person straight back here.
    expect(router.currentRoute.value.query.next).toBe('/join/t-1#token=tok-abc')
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
        members: [you, bob],
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
        members: [you, bob],
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
        members: [you, bob],
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
describe('SettleUpSheet Family mode', () => {
  // A complete partition is built incrementally (§7b): 4 people so a single build still leaves 2+
  // unassigned, keeping "Build a family" offered and the scenario worth narrating.
  const dana: MemberView = { id: 'm-dana', displayName: 'Dana', personHue: 4, claimed: true, isYou: false }
  const members = [you, bob, cara, dana]
  const fm = (m: MemberView): FamilyMemberView => ({
    id: m.id,
    displayName: m.displayName,
    personHue: m.personHue,
    isYou: m.id === you.id,
  })

  function mountFamily(rows: SettlementRow[] = []) {
    return mount(SettleUpSheet, {
      props: {
        open: true,
        tripId: 't-1',
        myMemberId: you.id,
        youAreCreator: true,
        rows,
        members,
        currencyCode: 'AUD',
        symbol: '$',
      },
      global: global(),
    })
  }

  async function buildFamily(sheet: ReturnType<typeof mount>, names: string[]) {
    await findByTestId(sheet, 'build-family').trigger('click')
    for (const name of names) {
      const row = findAllByTestId(sheet, 'person-toggle').find((r) => r.text().includes(name))!
      await row.trigger('click')
    }
    await findByTestId(sheet, 'family-builder-add').trigger('click')
    await flushPromises()
  }

  it('shows nothing until a family is built, and never fetches for an empty partition', async () => {
    const sheet = mountFamily()
    await nextTick()
    expect(findByTestId(sheet, 'mode-by-family').attributes('aria-pressed')).toBe('false')

    await findByTestId(sheet, 'mode-by-family').trigger('click')
    await flushPromises()

    // Nothing built yet reads as nothing to show — not four one-person "families" that are really
    // just the per-person view relabelled, and not a wasted request for a partition that's empty.
    expect(mocked.previewFamilies).not.toHaveBeenCalled()
    expect(findAllByTestId(sheet, 'family-card')).toHaveLength(0)
    expect(findByTestId(sheet, 'no-families-yet').exists()).toBe(true)
    expect(findByTestId(sheet, 'build-family').exists()).toBe(true)
  })

  it("building a family removes its members from the next builder's candidate list", async () => {
    mocked.previewFamilies!.mockResolvedValue({ families: [] })
    const sheet = mountFamily()
    await nextTick()
    await findByTestId(sheet, 'mode-by-family').trigger('click')
    await flushPromises()

    await findByTestId(sheet, 'build-family').trigger('click')
    // Nothing built yet: every trip member is a candidate.
    expect(findAllByTestId(sheet, 'person-toggle')).toHaveLength(4)
    await findByTestId(sheet, 'family-builder-cancel').trigger('click')

    await buildFamily(sheet, ['Bob', 'Cara'])
    expect(mocked.previewFamilies).toHaveBeenLastCalledWith('t-1', [[bob.id, cara.id]])

    await findByTestId(sheet, 'build-family').trigger('click')
    const secondBuilderRows = findAllByTestId(sheet, 'person-toggle')
    expect(secondBuilderRows).toHaveLength(2) // only Alice (You) and Dana remain
    expect(secondBuilderRows.some((r) => r.text().includes('Bob'))).toBe(false)
    expect(secondBuilderRows.some((r) => r.text().includes('Cara'))).toBe(false)
  })

  it('undoing the only built family returns to the empty state, and never emits changed', async () => {
    mocked.previewFamilies!.mockResolvedValueOnce({
      families: [
        {
          members: [fm(bob), fm(cara)],
          netMinor: 0,
          counterparts: [
            { members: [fm(you)], owedMinor: 0 },
            { members: [fm(dana)], owedMinor: 0 },
          ],
        },
        { members: [fm(you)], netMinor: 0, counterparts: [] },
        { members: [fm(dana)], netMinor: 0, counterparts: [] },
      ],
    }) // the only real fetch this test makes — building {Bob, Cara}

    const sheet = mountFamily()
    await nextTick()
    await findByTestId(sheet, 'mode-by-family').trigger('click')
    await flushPromises()
    expect(mocked.previewFamilies).not.toHaveBeenCalled() // nothing built yet, nothing fetched

    await buildFamily(sheet, ['Bob', 'Cara'])
    expect(findAllByTestId(sheet, 'family-card')).toHaveLength(3)
    expect(findAllByTestId(sheet, 'family-undo')).toHaveLength(1)

    await findByTestId(sheet, 'family-undo').trigger('click')
    await flushPromises()

    // Back to nothing built: the empty state, not a fresh fetch for a now-empty partition.
    expect(mocked.previewFamilies).toHaveBeenCalledTimes(1)
    expect(findAllByTestId(sheet, 'family-card')).toHaveLength(0)
    expect(findByTestId(sheet, 'no-families-yet').exists()).toBe(true)
    // Building and undoing a Family is a pure local/read affair — never the trip-changing act().
    expect(sheet.emitted('changed')).toBeUndefined()
  })

  it('resets Family mode and anything built when the sheet reopens', async () => {
    mocked.previewFamilies!.mockResolvedValue({
      families: [
        { members: [fm(bob)], netMinor: 0, counterparts: [] },
        { members: [fm(you)], netMinor: 0, counterparts: [] },
        { members: [fm(cara)], netMinor: 0, counterparts: [] },
        { members: [fm(dana)], netMinor: 0, counterparts: [] },
      ],
    })
    const sheet = mountFamily()
    await nextTick()
    await findByTestId(sheet, 'mode-by-family').trigger('click')
    await flushPromises()
    await buildFamily(sheet, ['Bob'])
    expect(findByTestId(sheet, 'mode-by-family').attributes('aria-pressed')).toBe('true')

    await sheet.setProps({ open: false })
    await sheet.setProps({ open: true })
    await nextTick()

    expect(findByTestId(sheet, 'mode-by-person').attributes('aria-pressed')).toBe('true')
    expect(findByTestId(sheet, 'mode-by-family').attributes('aria-pressed')).toBe('false')
    expect(findAllByTestId(sheet, 'family-card')).toHaveLength(0)
  })

  it('surfaces a failed build without disturbing the per-person view', async () => {
    mocked.previewFamilies!.mockRejectedValue(new Error('Network unreachable'))
    const sheet = mountFamily([
      {
        memberId: bob.id,
        displayName: 'Bob',
        personHue: 2,
        owedMinor: 6_000,
        pending: [],
        settled: [],
        rejected: [],
      },
    ])
    await nextTick()

    await findByTestId(sheet, 'mode-by-family').trigger('click')
    await flushPromises()
    // Nothing built yet, so nothing has even been requested — let alone failed.
    expect(mocked.previewFamilies).not.toHaveBeenCalled()

    await buildFamily(sheet, ['Bob', 'Cara'])

    expect(sheet.text()).toContain('Network unreachable')
    expect(findAllByTestId(sheet, 'family-card')).toHaveLength(0)

    await findByTestId(sheet, 'mode-by-person').trigger('click')

    // The per-person row is untouched by the failed Family fetch.
    expect(findByTestId(sheet, 'row-pay').exists()).toBe(true)
  })

  it('rolls back a rejected family and keeps the builder open to fix it, instead of getting stuck', async () => {
    // Regression test: onFamilyBuilt used to commit to builtFamilies before the server round-trip
    // and unconditionally close the builder. A rejection (e.g. this attempted partition, or any
    // other reason the server refuses one) then left builtFamilies holding a partition nothing on
    // screen matched: the builder was gone, no card carried Undo for it, and if the rejected
    // attempt had also consumed the last unassigned people, "Build a family" vanished too — no way
    // back short of closing the whole sheet. Now the builder simply stays open, selection intact,
    // until the server actually accepts what was built.
    mocked
      .previewFamilies!.mockResolvedValueOnce({
        families: [
          { members: [fm(bob), fm(cara)], netMinor: 500, counterparts: [] },
          { members: [fm(you)], netMinor: -250, counterparts: [] },
          { members: [fm(dana)], netMinor: -250, counterparts: [] },
        ],
      }) // building {Bob, Cara} — the first fetch, since nothing built yet fetches nothing
      .mockRejectedValueOnce(new Error('a trip needs at least two families')) // the failed attempt
      .mockResolvedValueOnce({
        families: [
          { members: [fm(bob), fm(cara)], netMinor: 500, counterparts: [] },
          { members: [fm(you)], netMinor: -250, counterparts: [] },
          { members: [fm(dana)], netMinor: -250, counterparts: [] }, // auto-singleton: never ticked
        ],
      }) // the corrected retry (You alone) — Dana falls out as a singleton, same as before the attempt

    const sheet = mountFamily()
    await nextTick()
    await findByTestId(sheet, 'mode-by-family').trigger('click')
    await flushPromises()
    expect(mocked.previewFamilies).not.toHaveBeenCalled() // nothing built yet, nothing fetched

    await buildFamily(sheet, ['Bob', 'Cara'])
    expect(findAllByTestId(sheet, 'family-card')).toHaveLength(3)

    await buildFamily(sheet, ['You', 'Dana'])
    expect(mocked.previewFamilies).toHaveBeenLastCalledWith('t-1', [
      [bob.id, cara.id],
      [you.id, dana.id],
    ])

    // Stuck would look like: no error, no builder, and no cards — the sheet's only way forward
    // being to close entirely. None of that: the builder is still right here, over the same two
    // candidates, with the error explaining what to change.
    expect(sheet.text()).toContain('a trip needs at least two families')
    expect(findAllByTestId(sheet, 'person-toggle')).toHaveLength(2) // still You and Dana, still open

    // Not just the same two candidates listed — the exact selection that was submitted must still
    // be ticked. Losing this silently (candidates unchanged, ticks wiped) was the actual bug: the
    // builder used to be torn down and recreated on a rejection, resetting its local `ticked` state.
    const youToggle = findAllByTestId(sheet, 'person-toggle').find((r) => r.text().includes('You'))!
    const danaToggle = findAllByTestId(sheet, 'person-toggle').find((r) => r.text().includes('Dana'))!
    expect(youToggle.attributes('aria-pressed')).toBe('true')
    expect(danaToggle.attributes('aria-pressed')).toBe('true')

    // Untick just Dana and retry — fixing the rejected selection means removing what's wrong, not
    // rebuilding the partition from scratch, because the tick state survived the rejection.
    await danaToggle.trigger('click')
    await findByTestId(sheet, 'family-builder-add').trigger('click')
    await flushPromises()

    expect(mocked.previewFamilies).toHaveBeenLastCalledWith('t-1', [[bob.id, cara.id], [you.id]])
    expect(findAllByTestId(sheet, 'family-card')).toHaveLength(3) // {Bob,Cara}, {You}, and Dana auto
    expect(findAllByTestId(sheet, 'family-undo')).toHaveLength(2) // both explicit families, not Dana
  })

  it('never emits changed just from switching modes back and forth', async () => {
    // A family already built, so re-entering Family mode below actually re-fetches — with nothing
    // built, switching modes fetches nothing at all, which would make this guard vacuous.
    mocked.previewFamilies!.mockResolvedValue({
      families: [
        { members: [fm(bob), fm(cara)], netMinor: 0, counterparts: [] },
        { members: [fm(you)], netMinor: 0, counterparts: [] },
        { members: [fm(dana)], netMinor: 0, counterparts: [] },
      ],
    })
    const sheet = mountFamily()
    await nextTick()
    await findByTestId(sheet, 'mode-by-family').trigger('click')
    await flushPromises()
    await buildFamily(sheet, ['Bob', 'Cara'])

    await findByTestId(sheet, 'mode-by-person').trigger('click')
    await findByTestId(sheet, 'mode-by-family').trigger('click')
    await flushPromises()

    expect(sheet.emitted('changed')).toBeUndefined()
  })

  it('discards a stale disband response that resolves after a newer one, instead of resurrecting the card it just removed', async () => {
    // Regression test for a request-ordering race in refreshFamilies(), which every caller
    // (onFamilyBuilt, disband, both watches) shares with no guard against an older request's
    // response overwriting a newer one. Reachable via disband alone: three explicit Families are
    // built (Bob, Cara, Dana), leaving You as the sole automatic singleton. Undo on Bob's card
    // fires a request for {Cara, Dana}; before it returns, Undo on Cara's card — a different,
    // still-live card — fires a second request for {Dana} alone. On a real network the two
    // responses can come back in either order; this test forces the OLDER request (Bob's) to
    // resolve LAST, after the NEWER one (Cara's) has already resolved and been applied.
    const built: FamiliesView = {
      families: [
        { members: [fm(bob)], netMinor: 0, counterparts: [] },
        { members: [fm(cara)], netMinor: 0, counterparts: [] },
        { members: [fm(dana)], netMinor: 0, counterparts: [] },
        { members: [fm(you)], netMinor: 0, counterparts: [] }, // auto singleton, never ticked
      ],
    }
    // What the server would have said for "minus Bob" alone — still carries Cara, because at the
    // moment this request was fired Cara had not been undone yet. This is the stale answer.
    const staleMinusBobOnly: FamiliesView = {
      families: [
        { members: [fm(cara)], netMinor: 0, counterparts: [] },
        { members: [fm(dana)], netMinor: 0, counterparts: [] },
        { members: [fm(you)], netMinor: 0, counterparts: [] },
      ],
    }
    // "minus both Bob and Cara" — the correct, most-recently-requested state.
    const freshMinusBoth: FamiliesView = {
      families: [
        { members: [fm(dana)], netMinor: 0, counterparts: [] },
        { members: [fm(you)], netMinor: 0, counterparts: [] },
      ],
    }

    let resolveStale!: (value: FamiliesView) => void
    const stale = new Promise<FamiliesView>((resolve) => {
      resolveStale = resolve
    })

    mocked
      .previewFamilies!.mockResolvedValueOnce({ families: [] }) // building Bob
      .mockResolvedValueOnce({ families: [] }) // building Cara
      .mockResolvedValueOnce(built) // building Dana — the state the screen renders from below
      .mockImplementationOnce(() => stale) // Undo Bob: left hanging, resolved by hand below
      .mockResolvedValueOnce(freshMinusBoth) // Undo Cara: answers immediately

    const sheet = mountFamily()
    await nextTick()
    await findByTestId(sheet, 'mode-by-family').trigger('click')
    await flushPromises()

    await buildFamily(sheet, ['Bob'])
    await buildFamily(sheet, ['Cara'])
    await buildFamily(sheet, ['Dana'])
    expect(findAllByTestId(sheet, 'family-card')).toHaveLength(4)

    const cardFor = (name: string) =>
      findAllByTestId(sheet, 'family-card').find((card) => card.text().includes(name))!

    // Undo Bob: fires the request for {Cara, Dana} and leaves it hanging (the deferred promise).
    await cardFor('Bob').find(testId('family-undo')).trigger('click')
    // Undo Cara, on a different, still-live card, before Bob's request has resolved: fires the
    // second request for {Dana} alone, which resolves right away.
    await cardFor('Cara').find(testId('family-undo')).trigger('click')
    await flushPromises()

    // The newer request has already answered; only now does the stale, older one resolve.
    resolveStale(staleMinusBobOnly)
    await flushPromises()

    // The newer request — Dana (and the You singleton) only — must win, regardless of which
    // resolved last. Bob's card is gone (undone first) and, critically, so is Cara's: the stale
    // response answering "minus Bob" alone must not resurrect the card the viewer already undid.
    expect(findAllByTestId(sheet, 'family-card')).toHaveLength(2)
    expect(sheet.text()).not.toContain('Bob')
    expect(sheet.text()).not.toContain('Cara')
    expect(sheet.text()).toContain('Dana')
  })
})

describe('TripScreen after the trip ends', () => {
  it('shows the ended badge and puts the add button away', async () => {
    mocked.trip!.mockResolvedValue(trip({ closedAt: '2026-08-18T03:00:00Z' }))
    mocked.settlement!.mockResolvedValue(emptySettlement)
    mocked.categories!.mockResolvedValue([])

    const screen = mount(TripScreen, { props: { tripId: 't-1' }, global: global() })
    await flushPromises()

    expect(findByTestId(screen, 'trip-ended').exists()).toBe(true)
    expect(findByTestId(screen, 'add-expense').exists()).toBe(false)
  })
})

describe('AddExpenseSheet with a receipt', () => {
  const categories = [
    { id: 'c-food', key: 'food', nameEn: 'Food', nameZh: '餐饮', icon: 'utensils', hue: 1, builtIn: true },
  ]

  async function sheetOnStepTwo() {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:receipt-preview')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    const sheet = mount(AddExpenseSheet, {
      props: { open: false, trip: trip(), categories },
      global: global(),
    })
    await sheet.setProps({ open: true })
    await nextTick()
    await findByTestId(sheet, 'key-5').trigger('click')
    await findByTestId(sheet, 'next-step').trigger('click')
    // Nobody is ticked by default now (spec §3); the All chip is the one-tap "it was everyone".
    await findByTestId(sheet, 'split-all').trigger('click')
    return sheet
  }

  async function pickPhoto(sheet: ReturnType<typeof mount>, name = 'bill.png') {
    const input = findByTestId(sheet, 'receipt-input')
    const photo = new File([new Uint8Array([1, 2, 3])], name, { type: 'image/png' })
    Object.defineProperty(input.element, 'files', { value: [photo] })
    await input.trigger('change')
    return photo
  }

  it('previews the picked photo and uploads it against the minted id after saving', async () => {
    const pinned = 'cafebabe-dead-4eef-cafe-babedead4eef'
    vi.spyOn(crypto, 'randomUUID').mockReturnValue(pinned)
    mocked.createItem!.mockResolvedValue(item({}))
    mocked.uploadReceipt!.mockResolvedValue(item({}))

    const sheet = await sheetOnStepTwo()
    const photo = await pickPhoto(sheet)
    expect(findByTestId(sheet, 'receipt-preview').exists()).toBe(true)

    await findByTestId(sheet, 'save-expense').trigger('click')
    await flushPromises()

    expect(mocked.createItem).toHaveBeenCalled()
    // happy-dom decodes no images, so prepareReceipt falls back to the original file here — the
    // real downscale pipeline is proven in a browser by the e2e suite.
    expect(mocked.uploadReceipt).toHaveBeenCalledWith(pinned, photo, 'bill.png')
    expect(sheet.emitted('saved')).toBeTruthy()
  })

  it('keeps the sheet open with the reason when the photo upload fails', async () => {
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('cafebabe-dead-4eef-cafe-babedead4eef')
    mocked.createItem!.mockResolvedValue(item({}))
    mocked.uploadReceipt!.mockRejectedValue(new Error('That image is too big — keep it under 5 MB'))

    const sheet = await sheetOnStepTwo()
    await pickPhoto(sheet)
    await findByTestId(sheet, 'save-expense').trigger('click')
    await flushPromises()

    // The expense itself saved; retrying the save replays it (client-minted id) and re-uploads.
    expect(sheet.emitted('saved')).toBeFalsy()
    expect(sheet.text()).toContain('too big')
  })
})

describe('ItemDetailSheet receipt', () => {
  const detailWith = (over: Partial<ItemView> = {}) => ({
    ...item({ receipt: { version: 'v-1' }, ...over }),
    paybacks: [],
  })

  async function openSheet(tripView: TripView = trip()) {
    const sheet = mount(ItemDetailSheet, {
      props: { open: false, itemId: 'i-1', trip: tripView, categories: [] },
      global: global(),
    })
    await sheet.setProps({ open: true })
    await flushPromises()
    return sheet
  }

  it('shows the thumbnail, and tapping it opens the full-screen review', async () => {
    mocked.itemDetail!.mockResolvedValue(detailWith())
    const sheet = await openSheet()

    expect(findByTestId(sheet, 'receipt-image').attributes('src')).toBe('/api/items/i-1/receipt?v=v-1')

    await findByTestId(sheet, 'receipt-thumb').trigger('click')

    expect(findByTestId(sheet, 'receipt-lightbox').exists()).toBe(true)
    expect(findByTestId(sheet, 'receipt-full').exists()).toBe(true)
    expect(findByTestId(sheet, 'receipt-replace').exists()).toBe(true)
  })

  it('Escape closes the review, never the sheet under it', async () => {
    mocked.itemDetail!.mockResolvedValue(detailWith())
    const sheet = await openSheet()
    await findByTestId(sheet, 'receipt-thumb').trigger('click')

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', cancelable: true }))
    await nextTick()

    expect(findAllByTestId(sheet, 'receipt-lightbox')).toHaveLength(0)
    expect(sheet.emitted('close')).toBeFalsy()
  })

  it('removes the receipt from the review, through the API', async () => {
    mocked.itemDetail!.mockResolvedValue(detailWith())
    mocked.deleteReceipt!.mockResolvedValue(undefined)
    vi.stubGlobal('confirm', () => true)

    const sheet = await openSheet()
    await findByTestId(sheet, 'receipt-thumb').trigger('click')
    await findByTestId(sheet, 'receipt-remove').trigger('click')
    await flushPromises()

    expect(mocked.deleteReceipt).toHaveBeenCalledWith('i-1')
    expect(sheet.emitted('changed')).toBeTruthy()
    vi.unstubAllGlobals()
  })

  it('offers a plain viewer the review but none of the editing', async () => {
    mocked.itemDetail!.mockResolvedValue(detailWith({ payerMemberId: bob.id }))
    const sheet = await openSheet(trip({ youAreCreator: false }))

    await findByTestId(sheet, 'receipt-thumb').trigger('click')

    expect(findByTestId(sheet, 'receipt-lightbox').exists()).toBe(true)
    expect(findAllByTestId(sheet, 'receipt-replace')).toHaveLength(0)
    expect(findAllByTestId(sheet, 'receipt-remove')).toHaveLength(0)
  })

  it('offers no expense or receipt editing on an ended trip', async () => {
    mocked.itemDetail!.mockResolvedValue({ ...item({}), paybacks: [] })
    const sheet = await openSheet(trip({ closedAt: '2026-08-18T03:00:00Z' }))

    expect(findAllByTestId(sheet, 'edit-split-open')).toHaveLength(0)
    expect(findAllByTestId(sheet, 'delete-item')).toHaveLength(0)
    expect(findAllByTestId(sheet, 'receipt-add')).toHaveLength(0)
  })

  it('adds a receipt from the detail sheet when the expense has none', async () => {
    mocked.itemDetail!.mockResolvedValue({ ...item({ receipt: null }), paybacks: [] })
    mocked.uploadReceipt!.mockResolvedValue(item({}))

    const sheet = await openSheet()
    await findByTestId(sheet, 'receipt-add').trigger('click')
    const input = findByTestId(sheet, 'receipt-input')
    const photo = new File([new Uint8Array([1, 2, 3])], 'bill.jpg', { type: 'image/jpeg' })
    Object.defineProperty(input.element, 'files', { value: [photo] })
    await input.trigger('change')
    await flushPromises()

    expect(mocked.uploadReceipt).toHaveBeenCalledWith('i-1', photo, 'bill.jpg')
    expect(sheet.emitted('changed')).toBeTruthy()
  })

  it('replacing from the lightbox uploads through the same picker', async () => {
    mocked.itemDetail!.mockResolvedValue(detailWith())
    mocked.uploadReceipt!.mockResolvedValue(item({}))

    const sheet = await openSheet()
    await findByTestId(sheet, 'receipt-thumb').trigger('click')
    await findByTestId(sheet, 'receipt-replace').trigger('click')
    const input = findByTestId(sheet, 'receipt-input')
    const photo = new File([new Uint8Array([9, 9, 9])], 'better.png', { type: 'image/png' })
    Object.defineProperty(input.element, 'files', { value: [photo] })
    await input.trigger('change')
    await flushPromises()

    expect(mocked.uploadReceipt).toHaveBeenCalledWith('i-1', photo, 'better.png')
    expect(sheet.emitted('changed')).toBeTruthy()
  })
})

describe('InviteSheet trip lifecycle', () => {
  it('the creator ends the trip behind a confirm that names the retention window', async () => {
    mocked.closeTrip!.mockResolvedValue(trip({ closedAt: '2026-08-18T03:00:00Z' }))
    const asked: string[] = []
    vi.stubGlobal('confirm', (message: string) => (asked.push(message), true))

    const sheet = mount(InviteSheet, { props: { open: true, trip: trip() }, global: global() })
    await nextTick()
    await findByTestId(sheet, 'end-trip').trigger('click')
    await flushPromises()

    expect(asked[0]).toContain('14')
    expect(mocked.closeTrip).toHaveBeenCalledWith('t-1')
    expect(sheet.emitted('changed')).toBeTruthy()
    vi.unstubAllGlobals()
  })

  it('a plain member sees no end button at all', async () => {
    const sheet = mount(InviteSheet, {
      props: { open: true, trip: trip({ youAreCreator: false }) },
      global: global(),
    })
    await nextTick()

    expect(findAllByTestId(sheet, 'end-trip')).toHaveLength(0)
    expect(findAllByTestId(sheet, 'reopen-trip')).toHaveLength(0)
  })

  it('an ended trip offers reopen instead of end', async () => {
    mocked.reopenTrip!.mockResolvedValue(trip())
    const sheet = mount(InviteSheet, {
      props: { open: true, trip: trip({ closedAt: '2026-08-18T03:00:00Z' }) },
      global: global(),
    })
    await nextTick()

    expect(findAllByTestId(sheet, 'end-trip')).toHaveLength(0)
    await findByTestId(sheet, 'reopen-trip').trigger('click')
    await flushPromises()

    expect(mocked.reopenTrip).toHaveBeenCalledWith('t-1')
    expect(sheet.emitted('changed')).toBeTruthy()
  })

  const ended = { closedAt: '2026-08-18T03:00:00Z' }

  it('offers putting away only once the trip has ended', async () => {
    const live = mount(InviteSheet, { props: { open: true, trip: trip() }, global: global() })
    await nextTick()
    // The server refuses this with a 409, so the button that would earn one is not offered.
    expect(findAllByTestId(live, 'put-away-trip')).toHaveLength(0)

    mocked.hideTrip!.mockResolvedValue(trip({ ...ended, hiddenAt: '2026-08-18T04:00:00Z' }))
    const finished = mount(InviteSheet, { props: { open: true, trip: trip(ended) }, global: global() })
    await nextTick()
    await findByTestId(finished, 'put-away-trip').trigger('click')
    await flushPromises()

    expect(mocked.hideTrip).toHaveBeenCalledWith('t-1')
    expect(finished.emitted('changed')).toBeTruthy()
  })

  it('a trip already put away offers putting it back', async () => {
    mocked.unhideTrip!.mockResolvedValue(trip(ended))
    const sheet = mount(InviteSheet, {
      props: { open: true, trip: trip({ ...ended, hiddenAt: '2026-08-18T04:00:00Z' }) },
      global: global(),
    })
    await nextTick()

    expect(findAllByTestId(sheet, 'put-away-trip')).toHaveLength(0)
    await findByTestId(sheet, 'put-back-trip').trigger('click')
    await flushPromises()

    expect(mocked.unhideTrip).toHaveBeenCalledWith('t-1')
  })

  it('deleting names the money still unsettled before it asks', async () => {
    const asked: string[] = []
    vi.stubGlobal('confirm', (message: string) => (asked.push(message), true))
    await router.push('/trips/t-1')

    const sheet = mount(InviteSheet, {
      props: { open: true, trip: trip({ name: 'Osaka', unsettledMinor: 4_280 }) },
      global: global(),
    })
    await nextTick()
    await findByTestId(sheet, 'delete-trip').trigger('click')
    await flushPromises()

    expect(asked[0]).toContain('Osaka')
    // The figure is the whole point of asking twice about this one.
    expect(asked[0]).toContain('42.80')
    expect(mocked.deleteTrip).toHaveBeenCalledWith('t-1')
    // Nowhere to stay: the screen behind this sheet is a trip that no longer exists.
    expect(router.currentRoute.value.path).toBe('/')
    vi.unstubAllGlobals()
  })

  it('a square trip is deleted without inventing an amount to warn about', async () => {
    const asked: string[] = []
    vi.stubGlobal('confirm', (message: string) => (asked.push(message), true))

    const sheet = mount(InviteSheet, {
      props: { open: true, trip: trip({ name: 'Flat', unsettledMinor: 0 }) },
      global: global(),
    })
    await nextTick()
    await findByTestId(sheet, 'delete-trip').trigger('click')
    await flushPromises()

    expect(asked[0]).toContain('Flat')
    expect(asked[0]).not.toContain('0.00')
    vi.unstubAllGlobals()
  })

  it('says no and nothing happens', async () => {
    vi.stubGlobal('confirm', () => false)

    const sheet = mount(InviteSheet, { props: { open: true, trip: trip() }, global: global() })
    await nextTick()
    await findByTestId(sheet, 'delete-trip').trigger('click')
    await flushPromises()

    expect(mocked.deleteTrip).not.toHaveBeenCalled()
    vi.unstubAllGlobals()
  })

  it('a plain member is offered none of it', async () => {
    const sheet = mount(InviteSheet, {
      props: { open: true, trip: trip({ ...ended, youAreCreator: false }) },
      global: global(),
    })
    await nextTick()

    expect(findAllByTestId(sheet, 'put-away-trip')).toHaveLength(0)
    expect(findAllByTestId(sheet, 'delete-trip')).toHaveLength(0)
  })
})
