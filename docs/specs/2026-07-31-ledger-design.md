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
| **Payback** | money moving between two members, usually filed under an item | A gave Bob **$100** · B gave Bob **$100** (+ screenshot) |

An **Item** carries its own people list, editable forever — after payment, after the trip,
whenever. It divides among whoever is on that list right now, by its own split rule (equal by
default; a dragged weighting or typed amounts otherwise).

A **Payback** is a claim. It is `PENDING` until **the person owed** approves it — on an item
that is the payer, who fronted the money; on a trip-level settlement it is whoever the money is
going to. **Pending money counts for nothing** anywhere in the maths.

### The settle-up formula

```
paidOut(m)      = Σ item.amount        where item.payer == m
                + Σ payback.amount     where payback.from == m  and status == APPROVED

receivedBack(m) = Σ payback.amount     where payback.to == m         and status == APPROVED

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
| Fair share | Equalise the whole cost. Each item divides among its current people list by its own split rule: **Equal**, **Weighted** (the SplitBar drag), or **Exact** (typed amounts). |
| People list | **Per item.** Hotel, dinner and ski pass each have their own. Editable forever. |
| Roster changes | **Manual.** Adding a trip member changes no existing item until you edit it. No auto-update, no nagging. |
| New item default | **Nobody ticked.** An `All` chip selects everyone in one tap. |
| Payers | Exactly one per item. |
| Paybacks | Filed under an item, or trip-level with no item (the Settle-up screen). Amount + date + optional screenshot. |
| Approval | **The person owed**, or the trip's creator — but never the person paying, enforced with a 403 even when that person is the creator. On an item the person owed is the payer, who fronted the money; at trip level it is whoever the money is going to. Them ticking a name off themselves is instant, since the only agreement needed is their own. The creator is included so a trip cannot stall when somebody stops answering their phone — the cost being that a creator can mark a debt between two other people as settled — and a creator may vouch for their *own* payment only to a member who has never signed in and so cannot confirm it. That is deliberate; it was the one place §3 and §5 disagreed, and §5 won. |
| Rejection | Reject with a reason → avatar turns coral → claimant edits and resubmits. |
| Item state | `ALL_SQUARE` when every sharer's approved paybacks ≥ their share. Card greys out, sinks down. |
| Final settlement | **Recorded.** Tapping Pay sends a request to the person owed; it sits pending until they approve. Display-only is no longer possible — a pending approval is state. |
| Settle-up rows | **Bilateral.** One row per person: what you owe them, or they owe you. Not a globally minimised transfer set. |
| Undo | **Either side, any time**, before or after approval. The row returns to unpaid. |
| Remind | A nudge to someone who owes you. Changes no balance. |
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

*What actually shipped first (2026-08, zero-budget interim — the Cloud Run shape above remains the
target):* the boot jar with the SPA embedded runs under systemd on the owner's existing free-tier
VM, behind the nginx that already serves another app at the domain root; Ledger lives at the
sub-path `/ledger` (`server.servlet.context-path`, Vite `base`, both set at deploy time). Postgres
18 runs on the same VM over loopback instead of Cloud SQL; secrets come from a root-only env file
instead of Secret Manager; TLS terminates at nginx, so prod sets `forward-headers-strategy: native`.
Same-origin, cookie posture and derive-on-read are unchanged. The CSRF cookie is named
`LEDGER-XSRF` because the host is shared with another Spring app. See
`docs/deploy/2026-08-vm-deploy.md` for the runbook.

### `engine` — the whole business rule set

```kotlin
// All money is Long minor units. No Double, no BigDecimal, anywhere.
sealed interface SplitRule { Equal; Weighted(Map<MemberId, Int>); Exact(Map<MemberId, Long>) }

fun shares(totalMinor: Long, members: List<MemberId>, rule: SplitRule, salt: Long): Map<MemberId, Long>
fun settle(trip: Trip): Settlement                  // net per member + suggested transfers
fun Trip.itemState(itemId: ItemId): ItemState       // OPEN | ALL_SQUARE
fun owesBetween(trip: Trip, a: MemberId, b: MemberId): Long   // the Settle-up rows
```

**Rounding — largest remainder.** Floor every share, then hand the leftover cents to the largest
fractional parts. Parts sum to the total exactly, by construction. Ties break on position
rotated by
`salt = itemId` so the spare cent lands on different people on different items. Minor-unit digits
come from `java.util.Currency.getInstance(code).defaultFractionDigits`, so JPY (0 decimals) works
without a special case.

**Suggested transfers.** Greedy: largest creditor against largest debtor, repeat. At most `n − 1`
transfers; typically 3–4 instead of 13.

### Patterns, and why each one is here

Chosen because this product's shape demands them, not for their own sake.

| Pattern | Where | Why this product needs it |
|---|---|---|
| **Ports & adapters** | `engine` is the domain core; `server` holds the REST adapter in and the JPA adapter out | The rules are the risky part. Keeping them free of Spring and SQL is what lets 2,000 random trips be verified in under a second. |
| **Read model / projection** | `TripView`, `OverviewView`, `ActivityView`, `SettlementView` | Every screen is a different projection of the same ledger. No number is ever stored — `owed`, `net` and `ALL_SQUARE` are all derived, so a corrected people list can never leave a stale total behind. This is the single most important consequence of the design. |
| **Command objects** | `CreateItem`, `PatchItemPeople`, `ApprovePayback` … | The write side is small and each command has exactly one authorisation rule. Keeps permission checks in one obvious place per operation rather than scattered through controllers. |
| **Strategy** | `IdentityProvider` → `Google` \| `Mock` | Dev must run with no Google credentials, and the WeChat-to-Google switch showed the auth choice is not settled. One interface, swapped by Spring profile. |
| **Repository** | One per aggregate: trips, items, paybacks | Standard, and it keeps Testcontainers tests honest — they exercise real SQL, not a mock. |
| **Assembler** | Entity → DTO, at the edge | The API contract is the demo's shape (`splits`, `yourShare`, `hue`). The database shape is normalised. Letting entities leak into JSON would weld them together. |

Explicitly **not** used: no event sourcing, no CQRS write/read split at the storage layer, no
generic "service layer" that just forwards to repositories. This is a small app; each of those
would add indirection without removing any real problem.

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

A name can be claimed once, and one person can hold one slot per trip. The second rule is the one
that matters: two slots would let somebody owe and be owed as two different people on the same
trip, and the balances would still sum to zero while being nonsense. The token is stateless and
therefore cannot be withdrawn before it expires — rotating the signing secret invalidates every
outstanding link at once, and is the only revocation there is. That is why validity is measured in
days.

---

## 5. Data model (Flyway `V1__init.sql`)

```sql
users(id, provider, subject, email, display_name, photo_url, created_at)
    unique(provider, subject)

trips(id, name, icon, hue, currency_code, created_by_user_id,
      starts_on, ends_on, created_at, archived_at)
                                           -- icon: a Lucide slug (plane, house, coffee)
                                           -- hue:  1..8, the disc colour on GroupCard

trip_members(id, trip_id, display_name, person_hue, user_id NULL, created_at)
    unique(trip_id, display_name)          -- user_id NULL until claimed
                                           -- person_hue 1..8, round-robin, never changes

categories(id, trip_id NULL, key, name_en, name_zh, icon, hue, sort_order)
                                           -- trip_id NULL = built-in; non-null = user-added

items(id, trip_id, title, category_id, amount_minor BIGINT, payer_member_id,
      spent_on, note, created_by_user_id, created_at, updated_at, version BIGINT)
                                           -- amount_minor is bounded on input (≤ 1e12): every
                                           -- balance is a sum into a Long, so an unbounded amount
                                           -- could wrap the sum past Long.MAX and break the two
                                           -- invariants under a 200.
                                           -- version: optimistic lock. Two edits from the same
                                           -- starting state cannot both land — the second gets a
                                           -- 409, not a silent overwrite of the first's people list.

items ... + split_rule                     -- EQUAL | WEIGHTED | EXACT

item_shares(trip_id, item_id, member_id, weight NULL, exact_amount_minor NULL)
                                           -- PK(item_id, member_id). The people list.
                                           -- weight is set for WEIGHTED, exact_amount_minor
                                           -- for EXACT; both null for EQUAL. Shares are
                                           -- still derived, never stored.
                                           -- weight ≥ 0: zero is legal (on the bill for the
                                           -- record, owing nothing), matching the engine and the
                                           -- browser's split port. Only a negative is refused.
                                           -- trip_id is here so both foreign keys can be
                                           -- composite — see "Trip scoping" below.

paybacks(id, trip_id, item_id NULL, from_member_id, to_member_id,
         amount_minor BIGINT, paid_on, proof_object_name NULL, note, status,
         created_by_user_id, created_at,
         reviewed_by_user_id NULL, reviewed_at NULL, reject_reason NULL)
                                           -- item_id NULL = a trip-level settlement from
                                           -- the Settle-up screen. Same table, same state
                                           -- machine, same approval rule. See section 7a.
```

No separate settlements table: a settlement **is** a payback with no item. One record type,
one state machine, one approval rule — and no way for an item repayment and a trip-level
settlement between the same two people to both subtract.
No computed share amounts are stored — they are derived on read, so editing a people list can
never leave a stale total behind. `weight` and `exact_amount_minor` are *inputs* to that
derivation, not results of it.

**Trip scoping.** Every foreign key that reaches a member or an item is composite and carries
`trip_id`: `items.payer_member_id`, both member columns on `paybacks`, and both keys on
`item_shares` all point at `(trip_id, id)`, which `trip_members` and `items` expose as a `UNIQUE`
constraint for exactly this purpose. A plain `REFERENCES trip_members (id)` would let one trip's
member be put on another trip's item, and the consequence is not cosmetic — that person is handed
a share of money they are not part of, and **both** trips' balances stop summing to zero. The
database refuses it rather than trusting the service to remember.

The one reference not scoped this way is `items.category_id`, because a category is either a
built-in (`trip_id IS NULL`) or the trip's own, and "null or equal" is not expressible as a foreign
key. A mis-scoped category is a wrong label, not wrong arithmetic; the service enforces it.

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
| generate a share link | trip creator — it is how the roster gets filled, so it follows the roster rule |
| claim a member slot | anybody holding a valid link for that trip; the link *is* the authorisation |
| create item, add a custom category | any trip member |
| edit or delete item (amount, category, date, **people list**) | item payer + trip creator |
| submit a payback claim | the claiming member |
| approve / reject a payback | item payer + trip creator |
| view everything | any trip member |

---

## 6. API surface

**Derived from the demo, not invented.** Every endpoint below exists because a screen in
`ui_kits/mobile-app/` needs it. Anything no screen needs is not here.

### What each screen actually needs

| Screen (`Screens.jsx`) | Needs | Endpoint |
|---|---|---|
| `GroupsHome` | every group's name, icon, hue, member avatars, your net; overall net **per currency** (never summed across currencies — ¥ added to $ is a meaningless figure); count settled | `GET /api/trips` |
| `OverallScreen` | net **per person across all groups**, and which groups each debt came from; total spent; what you fronted | `GET /api/overview` |
| `GroupDetail` | balance hero, three stats, who-owes-who rows, members, currency, start date, expenses grouped by day, filters | `GET /api/trips/{id}` |
| `ExpenseDetailSheet` | title, category, date, total, your share, payer, per-person splits, note | *(in the trip payload — see below)* |
| `ExpenseDetailSheet` — approval | who has paid the payer back, each one's status, proof thumbnails | `GET /api/items/{id}` |
| `Activity` | every expense across **all** groups, newest first, tagged with its group | `GET /api/activity` |
| `You` | your name, email, avatar; friends with a shared-group count; currency; sign out | `GET /api/me` |
| `AddExpenseSheet` | member list, category list, then save | `GET /api/trips/{id}/categories`, `POST /api/trips/{id}/items` |
| `SettleUpSheet` | who owes who, and the shortest way to clear it | `GET /api/trips/{id}/settlement` |
| `AppBar` invite action | a shareable link | `POST /api/trips/{id}/invite` |

### The endpoints

```
POST   /api/auth/session            { idToken }  → sets HttpOnly cookie
DELETE /api/auth/session            sign out — "You" screen
DELETE /api/auth/sessions           sign out everywhere — every device, not just this one
GET    /api/me                      profile + friends + shared-group counts

GET    /api/trips                   every group: icon, hue, members, your net
POST   /api/trips                   "New group" chip on GroupsHome
GET    /api/trips/{id}              the whole group detail screen in one call
POST   /api/trips/{id}/members
PATCH  /api/trips/{id}/members/{memberId}   { displayName } — creator fixes a typo'd name
POST   /api/trips/{id}/invite       → signed share-link token
POST   /api/trips/{id}/claimable    { token } — the link's landing page: trip name, unclaimed
                                    names, and `you` when the caller already holds a seat
POST   /api/trips/{id}/claim        { token, memberId }
GET    /api/trips/{id}/categories   eight built-ins + this trip's custom ones
POST   /api/trips/{id}/categories   { name, icon, hue }

POST   /api/trips/{id}/items        AddExpenseSheet save; optional client-supplied id,
                                    replayed → 200 rather than a second expense
GET    /api/items/{id}              paybacks in full: status, proof, dates, reject reasons
PATCH  /api/items/{id}              ← this is where the people list gets fixed
DELETE /api/items/{id}              the bin button on ExpenseDetailSheet

POST   /api/items/{id}/paybacks     { fromMemberId, amountMinor, paidOn, note }
POST   /api/paybacks/{id}/proof     multipart → Cloud Storage
POST   /api/paybacks/{id}/approve
POST   /api/paybacks/{id}/reject    { reason }
PATCH  /api/paybacks/{id}           claimant corrects a rejected claim → back to PENDING

GET    /api/trips/{id}/settlement   bilateral rows: your position with each person
POST   /api/trips/{id}/settlements  { toMemberId, amountMinor } — the Pay button
POST   /api/paybacks/{id}/undo      either side, before or after approval
POST   /api/trips/{id}/remind       { memberId } — a nudge; changes no balance
GET    /api/trips/{id}/expenses.csv the outward spend as a downloadable file — expenses only
GET    /api/overview                OverallScreen — cross-group, one call
GET    /api/activity                Activity tab — cross-group feed
```

### Added, because the demo needs them

- **`GET /api/overview`** — `OverallScreen` nets each person across *every* group and lists
  which groups each debt came from. Assembling that client-side would mean one call per group.
- **`GET /api/activity`** — the Activity tab is a flat cross-group feed. Nothing else serves it.
- **`GET /api/me` carries friends** — the "You" screen lists friends with a shared-group count.
  Folded into `/api/me` rather than a second endpoint; it is the same page load.
- **`trips.icon` and `trips.hue`** — `GroupCard` renders a Lucide glyph on a coloured disc
  (`plane`, `house`, `coffee`). Neither column existed. Added to the schema.
- **`DELETE /api/auth/sessions`** — sessions last 30 days, which is the right feel for something
  used on a phone during a trip. Without server-side revocation that also means a lost handset
  stays signed in long after the trip ends, and the money it can move is real. Sessions live in
  Postgres, so ending all of a user's is a lookup on the indexed `PRINCIPAL_NAME` column.
- **`PATCH /api/trips/{id}/members/{memberId}`** — added from live use: a member's name is the
  roster's one hand-entered fact, so it is the one that gets typed wrong, and before this the
  only fix was living with the typo. Renaming follows the roster rule (creator only, even for a
  claimed seat) and moves no number — nothing financial hangs off a display name. Re-casing a
  seat's own name is not a collision; landing on somebody else's is the same 409 as adding it.
- **`POST /api/trips/{id}/claimable`** — added with the screens (step 9). The claim flow's
  landing page has to show a friend which names are still free, and the friend is by definition
  not yet on the trip, so `GET /api/trips/{id}` correctly 404s for them. Like `claim`, the token
  is the authorisation; unlike `claim`, it answers with the trip's name and unclaimed names only —
  no items, no balances, no claimed members. The one exception is `you`, the caller's own seat when
  they already hold one: telling somebody what they already are leaks nothing about anyone else,
  and it lets the join screen offer the trip instead of a list of names every one of which `claim`
  could only refuse with a 409. The token travels in the request body (and in the
  share link's URL *fragment*), never a query string, which would copy it into access logs and
  Referer headers.

- **`GET /api/trips/{id}/expenses.csv`** — added by request once real trips were being reviewed:
  a record of the money that left the group, kept outside the app. One row per expense — date,
  recorded-at timestamp, payer, title, exact amount, currency — and deliberately nothing else:
  no paybacks or settlements (internal movement, not spend), no shares or participants (the
  reviewer wants the spend journal, not the debt graph). Amounts are exact major units derived
  from integer minor units by string arithmetic; the file is RFC 4180 with a UTF-8 BOM so
  spreadsheets read 中文 titles correctly. Same visibility as the trip: a stranger gets 404.
  The spend *time of day* is not captured by the app, so the export carries the spend date plus
  the row's write timestamp rather than inventing one.

### Removed, because nothing needs them

- **`DELETE /api/trips/{id}/members/{memberId}`** — no screen removes a member. S3 (someone
  cancels) is served by editing that item's people list via `PATCH /api/items/{id}`, which is
  the mechanism the whole design rests on. Add member-removal back when a screen calls for it.

### One deliberate shape decision

`GET /api/trips/{id}` returns each item **with its splits**, so `ExpenseDetailSheet` opens with
no second request — matching the demo, where tapping a row shows the sheet instantly. Paybacks
are *not* in that payload: they are unbounded per item and only the detail sheet's approval
section uses them, so they come from `GET /api/items/{id}` when the sheet opens.

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
- **SplitBar is fully interactive.** The drag produces a weight per person, which the server
  turns into exact cents. Weights go over the wire rather than pre-multiplied amounts, so the
  rounding is done once, in the engine, by largest remainder.
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

## 7a. The Settle-up screen

Taken from the supplied screenshot, which is a faithful render of the demo's `SettleUpSheet`.
Three of its behaviours contradicted the earlier design; this section is the resolution.

### Rows are bilateral

One row per person — what you owe them, or they owe you — not a globally minimised transfer
set. `settle()` already emits transfers between arbitrary pairs (`Fei → Cara`) that the viewer
is not party to and which have no row on this screen.

```
owesBetween(A, B) =   Σ A's share of items B paid for
                    − Σ approved paybacks A → B
                    − Σ B's share of items A paid for
                    + Σ approved paybacks B → A
```

Positive means A owes B. This is consistent with the existing net by construction:

```
Σ over all B of owesBetween(A, B)  ==  −net(A)
```

which is why the screenshot's three rows (−39.10, −46.00, +42.30) add up to the −42.80 on the
group's hero card. That identity is a property test, not a comment.

### Pay is a request, not an act

The person paying cannot approve their own claim, so tapping **Pay** cannot settle anything on its
own. Only the person owed — or the trip's creator — can (§3), and this is enforced on the server,
not merely stated: a creator who is *also* the one paying is refused (403), because the recipient's
agreement is the whole point. The one exception is a creator vouching for a payment to a member who
has **never signed in** — that person cannot confirm it themselves, so without the creator the debt
to a ghost would stall forever (§5). The confirmation itself lives on the Settle-up strip: a
trip-level settlement has no bill, so the recipient approves, rejects, or the claimant withdraws it
there, never on an item sheet.

```
  tap Pay  →  PENDING          "Sent to Mei for confirmation"   counts as unpaid
  Mei approves  →  APPROVED    green tick                        counts as paid
  Mei rejects   →  REJECTED    with a reason                     counts as unpaid
  either side undoes  →  gone  row returns to unpaid             any time, either state
```

**Undo is available to both parties, before and after approval.** A settled trip can therefore
un-settle; that is the accepted cost of never trapping someone in a wrong record.

**Remind** nudges someone who owes you. It changes no balance and writes no payback.

**"Done for now"** just closes the sheet. The group reaches all-square when every row is
approved — it is a derived state, never a button.

### What this costs the engine

`Payback` currently hangs off `Item` and is implicitly *to* that item's payer. A trip-level
settlement has no item, so it needs an explicit recipient. The change:

```kotlin
// Paybacks move up to Trip and carry both ends plus an optional item.
data class Payback(
    val from: MemberId,
    val to: MemberId,
    val amountMinor: Long,
    val status: PaybackStatus,
    val itemId: ItemId? = null,   // null = a trip-level settlement
)

fun owesBetween(trip: Trip, a: MemberId, b: MemberId): Long
```

`itemState` then takes the trip's paybacks filtered to that item rather than reading them off
`Item` directly. `settle()` is unaffected in shape — a settlement adds to the sender's paid-out
and the recipient's received-back exactly as an item payback does, so `Σ net == 0` still holds.

---

## 8. Build order

Each step ends with something runnable and tested.

1. **Spec** — this document. ✔
2. **`engine`** — `shares`, `settle`, `itemState`, and S1–S6 as tests. No Spring yet.
   *This is where the app is proven correct.* ✔
2a. **`engine` — bilateral balances.** ✔ `Payback` moved onto `Trip` with an explicit recipient
   and an optional item; `owesBetween` added and property-tested to sum to `−net` across 500
   random trips. Required by the Settle-up screen (§7a).
3. **`server` skeleton** — Spring Boot 4 + Flyway + Postgres via Testcontainers, `/api/me`,
   `MockIdentityProvider`, session cookie. ✔ `V1__init.sql` builds the whole of §5 and seeds the
   eight built-in categories; sessions are Spring Session JDBC rows in Postgres rather than
   in-memory, because Cloud Run will not always answer on the instance that signed you in.
   `POST`/`DELETE /api/auth/session` and `GET /api/me` are live behind Spring Security with CSRF
   on and a `NullRequestCache`, so anonymous traffic creates no session.
4. **Trips + members + claim flow** — endpoints and permission tests. ✔ Creating a trip makes you
   its first member, already claimed. A trip you are not on returns 404, not 403 — a stranger
   should not be able to confirm it exists. Invite tokens are HMAC-signed over trip and expiry,
   with the secret supplied per environment and no default anywhere, so a profile without one
   refuses to start. `yourNetMinor` comes from the engine over an empty item list rather than a
   literal zero, so the mapping step 5 needs is already exercised.
5. **Items + categories** — CRUD, people list editing, live shares, custom categories. ✔ The hotel
   case is covered end to end over HTTP: two $1,000 bills for thirteen, a fourteenth person ticked
   onto both people lists, and every share re-derived with the bills still adding up to the cent.
   Shares are never stored — `item_shares` holds only the inputs. The engine's split salt comes
   from the item's UUID, which fixes that mapping permanently.
6. **Paybacks + approval** — submit / approve / reject, pending excluded from maths, `ALL_SQUARE`. ✔
   §10's end-to-end check now runs as a test: thirteen people, two $1,000 bills, nine paying $100
   and twelve paying $76.92, Jack ticked on at the end — landing on Bob +$34.10, the nine +$34.06,
   the three joiners −$65.94, Jack −$142.86, and the column summing to exactly zero. The
   Settle-up screen's own endpoints (bilateral rows, trip-level settlements, remind) are the
   remaining part of §7a and come next.
6a. **Settle-up screen endpoints.** ✔ Bilateral rows (`GET /api/trips/{id}/settlement`), Pay as a
   pending request (`POST /api/trips/{id}/settlements`), and Remind. §7a specified these but §8
   never gave them a step of their own, so they are recorded here rather than left to fall between
   two. The `Σ owesBetween(A, B) == −net(A)` identity is asserted over HTTP as well as
   property-tested in the engine. **Remind validates but delivers nothing** — see §9.

7. **Screenshot upload** — Cloud Storage, signed read URLs.
8. **Tally → Vue port** — tokens, then presentational, then interactive components. Tokens and the
   presentational set are done; SplitBar, AmountInput and PersonToggleRow follow. Two things did
   *not* port: Tally's `Amount` takes a major-unit float, and its SplitBar keeps float percentages.
   Both were rebuilt on integer minor units, and the SplitBar asks a TypeScript port of the
   engine's own largest remainder so the amounts shown while dragging are the amounts charged. The
   client mints the item id to make that possible, since the id is the split's salt. ✔ Complete:
   tokens, the presentational set, the forms, the lists, navigation and feedback. A third thing did
   not port — `GroupCard` decided "all square" with `Math.abs(balance) < 0.005`, a tolerance that
   only makes sense for floats. In whole cents it is `=== 0`, because four cents is not square.
9. **`web` screens** — 1–7 above, i18n EN/中文 scaffolding from the first component. ✔ Plus the
   share link's landing page, roster additions from the invite sheet, and fixing a bill's people
   list from its detail sheet — so the hotel case is performable end to end, which the first
   build of this step had quietly left impossible.
9a. **End-to-end suite.** ✔ Playwright drives the built app against the seeded backend (real
   Postgres, real HTTP): the hotel case through the glass, preview-equals-landed with the odd
   cent, the §7a round trip across two real browsers (pending moves nothing → reject with a
   reason → try again → approve → undo un-settles), double-save idempotency, the share link
   bounced through sign-in with its fragment intact, and a 中文 browser getting the whole app in
   Chinese. `Σ rows == hero` is asserted off the screen after every mutation — invariant 2 at
   the last boundary there is.
10. **Google Sign-In** — swap in `GoogleIdentityProvider` behind the same seam. Note: the interim
    deployment signs people in by name under the `name-signin` profile (`provider = "name"` in
    `users`); Google identities will be new rows, and linking them is part of this step.
11. **Deploy** — Cloud Run + Cloud SQL + Secret Manager. *Shipped 2026-08 in an interim shape:*
    free-tier VM + nginx sub-path `/ledger` + local Postgres 18 + env-file secrets + `name-signin`
    (owner's explicit zero-cost call; deviations recorded in §4). Moving to the Cloud Run shape
    stays open under this step.
12. **Motion pass** — Tally's spring curve on every state change, once behaviour is settled.

---

## 9. Deliberately not in phase 1

- **Multi-payer items.** One payer per item is how the group actually works.
- **Refunds** (hotel refunds part of a deposit). Would be a negative-amount item.
- **Globally minimised transfers.** `settle()` computes them and they are still property-tested,
  but no screen shows them — Settle-up is bilateral (§7a). Kept in the engine because it is the
  honest answer to "what is the least money that needs to move", and costs nothing to retain.
- **The demo's one-tap "mark everyone settled".** Replaced by the two-party approval, which was
  specified later and in more detail. The demo predates that requirement.
- **"Jack isn't on 5 items" prompts.** Roster editing is manual by choice; noted as a future
  convenience rather than built unasked.
- **Chinese copy.** i18n machinery ships; the Chinese voice pass is its own task.
- **Multi-currency, offline/PWA, receipt OCR, CSV export, push notifications, dark mode.**
  One consequence worth stating plainly rather than discovering: with no notifications,
  `POST /api/trips/{id}/remind` checks that the nudge makes sense — they are on the trip, and they
  really do owe you — and then does nothing. The endpoint exists so the button can be wired and the
  rule has a home. Nobody should be told it sent anything.

---

## 10. Verification

```bash
./gradlew :engine:test      # S1–S6. Must pass before any UI exists.
./gradlew :server:test      # Testcontainers Postgres. Permissions + approval state machine.
./gradlew :server:bootTestRun   # dev+demo profiles: mock login, seeded 14-person trip
docker compose up -d && \
  ./gradlew :server:bootRun --args='--spring.profiles.active=dev'
                            # the same app, empty, on a Postgres that keeps its data
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
