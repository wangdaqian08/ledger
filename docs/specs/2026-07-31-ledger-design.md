# Ledger — group trip expense splitting

Design spec. Agreed 2026-07-31.

## Context

A group of friends takes trips together (snow trips, camping). Costs get paid by whoever is
standing there — one person books the hotel, another buys dinner — and at the end nobody can
work out who is up and who is down.

The thing that breaks every existing tool is **headcount that changes after money has already
moved**. A hotel deposit is paid when 10 people are coming. The balance is paid when 13 are
coming. On arrival there are 14, because Jack turned up at check-in and hasn't paid a cent.
The hotel bill never changed — but every person's fair share did, three times. The ten who
already paid are now **owed money back**, and Jack owes the full share.

**Design goal: the arithmetic is never the hard part.** Keeping each item's people list correct
is. So the app is built around one primitive — an item with an editable list of people — and
every number is derived from it, live.

---

## 1. The model — two record types, nothing else

| | what it is | example |
|---|---|---|
| **Item** | money that left the group, paid by exactly one person | Bob paid **$1,000** hotel deposit, shared by 10 people |
| **Payback** | money moving between two members, filed **under an item** | A gave Bob **$100** · B gave Bob **$100** (+ screenshot) |

An **Item** carries its own people list, editable forever — after payment, after the trip,
whenever. Each item splits **equally** among whoever is on its list right now.

A **Payback** is a claim. It is `PENDING` until the item's payer approves it.
**Pending money counts for nothing** anywhere in the maths.

### The settle-up formula

```
paidOut(m)      = Σ item.amount        where item.payer == m
                + Σ payback.amount     where payback.from == m  and status == APPROVED

receivedBack(m) = Σ payback.amount     where payback.item.payer == m and status == APPROVED

owed(m)         = Σ share(m, item)     for every item whose people list contains m

net(m)          = paidOut(m) − receivedBack(m) − owed(m)
```

`net > 0` → is owed money.  `net < 0` → owes money.
**Invariant: `Σ net(m) == 0` across all members, always.** A property test, not a hope.

### Why the hotel case needs no special machinery

Editing the two hotel items' people lists to 14 is the entire fix:

```
deposit  $1,000 ÷ 14  =  $71.43
balance  $1,000 ÷ 14  =  $71.43
                        ────────
hotel, per person        $142.86     ( = $2,000 ÷ 14 )
```

No deposit-stage concept, no reconciliation pass, no rebalancing algorithm. Two lists.

---

## 2. Acceptance scenarios — literal unit tests

**S1 · The hotel, 10 → 13 → 14.** 14 members. Two items of $1,000, payer Bob, both lists set
to all 14. Paybacks: 9 originals × $100 on the deposit; 12 people × $76.92 on the balance
(Bob is one of the 13 and does not pay himself). Jack has paid nothing.

```
Bob                     +$34.10   gets back
the 9 originals (each)  +$34.06   gets back
the 3 who joined (each) -$65.94   still owes
Jack                   -$142.86   still owes
```

Bob lands 4¢ clear of the other nine because the real-world paybacks were round $76.92, not
the exact thirteenth. Correct behaviour, not drift. These figures are quoted against the
rounded $142.86 share; the test asserts **exact minor units**, where two of the fourteen carry
a share one cent lower and the column sums to precisely zero.

**S2 · Lucy's dinner.** $200, four people, Lucy paid. The other three owe $50 each. Item flips
to `ALL_SQUARE` the moment the third payback is approved.

**S3 · Headcount drops.** Same hotel, someone cancels, list goes to 9. Everyone's share rises
and the person who left shows a positive net.

**S4 · Pending doesn't count.** Submitting a claim moves no number. Approving it does.

**S5 · Rounding.** `$100 ÷ 3` → `33.34 + 33.33 + 33.33 = 100.00` exactly.
`¥10,000 ÷ 3` (JPY, zero decimals) → `3334 + 3333 + 3333`.

**S6 · Property.** Random trips, random edits → `Σ net == 0`, and every item's shares sum to
that item's exact amount.

---

## 3. Decisions

| Area | Decision |
|---|---|
| Fair share | Equalise the whole cost. Each item splits **equally** among its current people list. |
| People list | **Per item.** Hotel, dinner and ski pass each have their own. Editable forever. |
| Roster changes | **Manual.** Adding a trip member changes no existing item until you edit it. No auto-update, no nagging. |
| New item default | **Nobody ticked.** An `All` chip selects everyone in one tap. |
| Payers | Exactly one per item. |
| Paybacks | Filed under an item. Amount + date + optional screenshot. |
| Approval | Only the **item's payer** (or trip creator) approves. The payer ticking a name themselves is instant. |
| Rejection | Reject with a reason → avatar turns coral → claimant edits and resubmits. |
| Item state | `ALL_SQUARE` when every sharer's approved paybacks ≥ their share. Card greys out, sinks down. |
| Final settlement | **Display only.** Settle up shows who-pays-who; the app does not record that it happened. |
| Recalculation | Live everywhere, plus a dedicated Settle up screen. |
| Edit rights | Item payer + trip creator. Everyone else views and claims. |
| Currency | One per trip, ISO-4217. |
| Language | English + 中文 switchable. i18n from commit one; English strings first, Chinese voice pass as its own task. |
| Auth | **Google Sign-In.** Mock provider in dev. |
| Avatars | Tally person hue assigned round-robin and never changed; Google photo once that person links a login. |
| Design system | **Tally**, ported to Vue. See §7. |
| Categories | Tally's 8 built-ins, **plus user-added custom categories**. |

---

## 4. Architecture

Gradle multi-project. The important move: **the rules live in a module with no Spring and no
database**, so the scenarios above run in milliseconds.

```
ledger/
├── engine/     pure Kotlin. Split + settle + item state. Zero dependencies. Fully tested.
├── server/     Spring Boot 4, Kotlin, REST, Flyway, Postgres, Google auth, GCS uploads.
└── web/        Vue 3 + TS + Vite SPA, on the Tally design system. Packaged into the server jar.
```

**Stack:** Spring Boot 4.x · Kotlin 2.2+ · **Java 25 LTS** · Gradle 9 · Postgres (Cloud SQL) ·
Flyway · Vue 3 + TypeScript + Pinia + Vue Router + Vue I18n.

Verified against Spring Boot 4.1 docs: requires Java 17+ and is compatible up to and including
Java 26, so Java 25 LTS is in range. Spring Framework 7.0.8+, Kotlin 2.2+, Gradle 8.14+/9.x,
Servlet 6.1 (Tomcat 11). Set via a Gradle toolchain (`JavaLanguageVersion.of(25)`) so the build
is reproducible regardless of the developer's default JDK.

**Deployment:** one container on Cloud Run. The SPA is served from the same origin as the API —
no CORS, and the session cookie is `HttpOnly` + `SameSite=Lax` with no token ever touching
JavaScript. Cloud SQL Postgres, Cloud Storage for screenshots, Secret Manager for the OAuth secret.

### `engine` — the whole business rule set

```kotlin
// All money is Long minor units. No Double, no BigDecimal, anywhere.
fun splitEqually(totalMinor: Long, memberIds: List<MemberId>, salt: Long): Map<MemberId, Long>
fun settle(trip: TripSnapshot): Settlement          // net per member + suggested transfers
fun itemState(item: ItemSnapshot): ItemState        // OPEN | ALL_SQUARE
```

**Rounding — largest remainder.** `base = total / n`, `rem = total % n`. The first `rem` members
get `base + 1`. Parts sum to the total exactly, by construction. The starting offset rotates by
`salt = itemId` so the spare cent lands on different people on different items. Minor-unit digits
come from `java.util.Currency.getInstance(code).defaultFractionDigits`, so JPY (0 decimals) works
without a special case.

**Suggested transfers.** Greedy: largest creditor against largest debtor, repeat. At most `n − 1`
transfers; typically 3–4 instead of 13.

### `server`

Auth sits behind one seam so dev never needs real Google credentials:

```kotlin
interface IdentityProvider { fun verify(token: String): ExternalIdentity }
class GoogleIdentityProvider  // verifies the Google ID token (OIDC)
class MockIdentityProvider    // @Profile("dev") — log in as any name, no network
```

**Claim flow.** Trip creator types member names, generating a signed share link. A friend opens
it, signs in, and picks which name is them — that sets `trip_members.user_id`. Friends who never
sign in still work; the payer just ticks them off directly.

---

## 5. Data model (Flyway `V1__init.sql`)

```sql
users(id, provider, subject, email, display_name, photo_url, created_at)
    unique(provider, subject)

trips(id, name, currency_code, created_by_user_id, starts_on, ends_on, created_at, archived_at)

trip_members(id, trip_id, display_name, person_hue, user_id NULL, created_at)
    unique(trip_id, display_name)          -- user_id NULL until claimed
                                           -- person_hue 1..8, round-robin, never changes

categories(id, trip_id NULL, key, name_en, name_zh, icon, hue, sort_order)
                                           -- trip_id NULL = built-in; non-null = user-added

items(id, trip_id, title, category_id, amount_minor BIGINT, payer_member_id,
      spent_on, note, created_by_user_id, created_at, updated_at)

item_shares(item_id, member_id)            -- PK(item_id, member_id). The list. That's it.

paybacks(id, item_id, from_member_id, amount_minor BIGINT, paid_on,
         proof_object_name NULL, note, status, created_by_user_id, created_at,
         reviewed_by_user_id NULL, reviewed_at NULL, reject_reason NULL)
```

No `settlements` table — final settle-up is display-only.
No stored share amounts — they are derived, so editing a list can never leave stale numbers behind.
Per-person share **overrides** would be one nullable column on `item_shares`; not built now.

**Categories.** Eight built-ins, matching Tally's canonical Lucide glyphs:
`utensils` Food · `beer` Drinks · `car-front` Transport · `bed-double` Stay ·
`shopping-basket` Groceries · `ticket` Fun · `house` Home · `circle-dashed` Other.
A trip member can add a custom category — name, a glyph from the 38 vendored Lucide icons, and a
hue from the person ramp. Custom categories are scoped to their trip. **No emoji, ever** (Tally rule).

### Permissions

| action | who |
|---|---|
| create trip | any signed-in user |
| add / remove trip members | trip creator |
| create item, add a custom category | any trip member |
| edit or delete item (amount, category, date, **people list**) | item payer + trip creator |
| submit a payback claim | the claiming member |
| approve / reject a payback | item payer + trip creator |
| view everything | any trip member |

---

## 6. API surface (phase 1)

```
POST   /api/auth/session            { idToken }  → sets HttpOnly cookie
DELETE /api/auth/session
GET    /api/me

POST   /api/trips
GET    /api/trips
GET    /api/trips/{id}              → trip + members + item summaries + my net
POST   /api/trips/{id}/members
DELETE /api/trips/{id}/members/{memberId}
POST   /api/trips/{id}/invite       → signed share-link token
POST   /api/trips/{id}/claim        { token, memberId }
GET    /api/trips/{id}/categories
POST   /api/trips/{id}/categories   { name, icon, hue }

POST   /api/trips/{id}/items
GET    /api/items/{id}              → item + per-person share + paybacks + state
PATCH  /api/items/{id}              ← this is where the people list gets fixed
DELETE /api/items/{id}

POST   /api/items/{id}/paybacks     { fromMemberId, amountMinor, paidOn, note }
POST   /api/paybacks/{id}/proof     multipart → Cloud Storage
POST   /api/paybacks/{id}/approve
POST   /api/paybacks/{id}/reject    { reason }

GET    /api/trips/{id}/settlement   → net per member + suggested transfers
```

---

## 7. Frontend — the Tally design system

`Tally_Design_System/` is a complete, purpose-built system for this exact product: warm paper
`#FFFBF2`, ink `#1A1720` borders, one action colour Grape `#4B3BFF`, Mint = owed to you,
Coral = you owe. Every surface is a **slab** — 2px ink border, hard downward edge, no blur.
One spring curve `cubic-bezier(.34, 1.56, .64, 1)` at 90/160/240/380/620ms.

**It is the source of truth for anything visual.** Read `Tally_Design_System/readme.md` before
writing UI. Its `.d.ts` files are the prop contracts; its `.prompt.md` files are the usage notes.

### Porting

Components ship as React `.jsx` with inline styles over CSS custom properties. We are on Vue.

- `tokens/*.css` — used **unchanged**. No port needed.
- Presentational components (`Avatar`, `Badge`, `Card`, `Chip`, `Amount`, `Icon`, `ListRow`,
  `ExpenseRow`, `BalanceRow`, `GroupCard`, `EmptyState`, `ProgressBar`, `SettledBanner`,
  `AppBar`) — mechanical conversion to Vue SFCs.
- Interactive components (`Keypad`, `AmountInput`, `SplitBar`, `Sheet`, `Toast`, `TabBar`,
  `Stepper`, `PersonToggleRow`, `CategoryPicker`, `Button`/`IconButton` press states) — real
  porting work; behaviour is specified in each `.prompt.md`.
- `components/core/icon-paths.js` — plain JS, reusable as-is.

`ui_kits/mobile-app/` is a working click-through of the whole product. Keep it running as the
visual reference to diff against; it is a visual kit, not a ledger — its arithmetic is
deliberately naive and must not be copied.

### Deviations from Tally, and why

- **CJK fallback.** Figtree and Space Grotesk are Latin-only and Tally states it does no
  localisation. `--font-core` and `--font-money` gain **Noto Sans SC** as a fallback so 中文
  renders deliberately rather than by accident. Tally's voice rules (sentence case, no jargon,
  "All square") are English-specific and need a Chinese equivalent written as its own task.
- **Approval states.** Tally has no two-party approval. `Avatar` already takes a `badge` prop —
  that is the slot: unpaid = no badge · pending = `clock` · approved = `check` · rejected =
  `x` in coral.
- **SplitBar is read-only in phase 1.** Our model is equal-split-only, so its drag interaction
  has nothing to drive yet. The kit already supports a read-only SplitBar for expense detail.
- **Custom categories** extend Tally's fixed set of 8, per product requirement.

### Screens (phase 1)

1. **Sign in** — one Google button. Dev build shows a name picker.
2. **Trips** — `GroupCard` tiles with member `AvatarStack`, overall balance hero.
3. **Trip home** — balance hero, items grouped by calendar day as `ExpenseRow`s, raised `+`.
4. **Add item** — `Keypad` amount → `CategoryPicker` → who paid → `PersonToggleRow`
   (nobody ticked, `All` chip). Live "$71.43 each" as you tick.
5. **Item detail** — `Sheet`: total, who fronted it, the avatar row where each avatar carries
   its own badge, per-person share rows, payback list with screenshot thumbnails,
   approve/reject for the payer. `SettledBanner` when `ALL_SQUARE`.
6. **Claim payback** — `Sheet`: amount pre-filled with what you owe, date, optional screenshot.
7. **Settle up** — `BalanceRow` list (mint owed / coral owes), then the shortest who-pays-who.

---

## 8. Build order

Each step ends with something runnable and tested.

1. **Spec** — this document. ✔
2. **`engine`** — `splitEqually`, `settle`, `itemState`, and S1–S6 as tests. No Spring yet.
   *This is where the app is proven correct.*
3. **`server` skeleton** — Spring Boot 4 + Flyway + Postgres via Testcontainers, `/api/me`,
   `MockIdentityProvider`, session cookie.
4. **Trips + members + claim flow** — endpoints and permission tests.
5. **Items + categories** — CRUD, people list editing, live shares, custom categories.
6. **Paybacks + approval** — submit / approve / reject, pending excluded from maths, `ALL_SQUARE`.
7. **Screenshot upload** — Cloud Storage, signed read URLs.
8. **Tally → Vue port** — tokens, then presentational, then interactive components.
9. **`web` screens** — 1–7 above, i18n EN/中文 scaffolding from the first component.
10. **Google Sign-In** — swap in `GoogleIdentityProvider` behind the same seam.
11. **Deploy** — Cloud Run + Cloud SQL + Secret Manager.
12. **Motion pass** — Tally's spring curve on every state change, once behaviour is settled.

---

## 9. Deliberately not in phase 1

- **Per-person share overrides** (unequal splits, couple sharing a room). One nullable column when needed.
- **Multi-payer items.** One payer per item is how the group actually works.
- **Refunds** (hotel refunds part of a deposit). Would be a negative-amount item.
- **Recording final settlement transfers.** Settle up displays; it does not track.
- **"Jack isn't on 5 items" prompts.** Roster editing is manual by choice; noted as a future
  convenience rather than built unasked.
- **Chinese copy.** i18n machinery ships; the Chinese voice pass is its own task.
- **Multi-currency, offline/PWA, receipt OCR, CSV export, push notifications, dark mode.**

---

## 10. Verification

```bash
./gradlew :engine:test      # S1–S6. Must pass before any UI exists.
./gradlew :server:test      # Testcontainers Postgres. Permissions + approval state machine.
./gradlew :server:bootRun   # dev profile: mock login, seeded 14-person trip
npm --prefix web run test   # Vitest on stores and computed shares
npm --prefix web run e2e    # Playwright: full approval round-trip
```

**End-to-end manual check — the scenario that justifies the app:**

1. Create a trip with 13 members. Add two $1,000 hotel items, payer Bob, all 13 on each list.
2. Add the paybacks: 9 × $100 on the deposit, 12 × $76.92 on the balance. Approve them all as Bob.
3. Add Jack as a 14th member. **Nothing changes yet** — the manual model working as chosen.
4. Edit both hotel items, tick Jack in.
5. Settle up must now read: Bob **+$34.10**, the 9 originals **+$34.06** each, the 3 stage-2
   joiners **−$65.94** each, Jack **−$142.86** — give or take the one cent largest-remainder
   hands to some members and not others. The column must sum to exactly zero.
6. Add Lucy's $200 dinner for 4. Claim as Ben. It stays pending and moves no number. Approve as
   Lucy → Ben goes green. Approve the other two → the card flips to all square.
