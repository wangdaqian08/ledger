# The demo's screens, verified by clicking through them

`tally-demo.html` is the click-through prototype for the intended screens. This map records a
full walk of its navigation (Playwright, 2026-08-06) and is the reference for build-order step 9:
every screen and edge below is one the real Vue app needs.

The prototype is **layout and navigation only**. Its arithmetic is wrong on purpose to us: with
three people sharing $96.40 it shows $32.13 + $32.13 + $32.13 = $96.39 — it divides floats and
rounds each share on its own, so a cent vanishes. `web/src/lib/split.ts` answers 32.14 + 32.13 +
32.13. This is the worked example of the CLAUDE.md rule: read the reference for behaviour and
layout, never for arithmetic.

## Screens

| Screen | Reached from | What it holds |
|---|---|---|
| Groups (home) | app start; Groups tab | overall position card, group cards with per-group "you owe / you get / All square" |
| Group detail | tap a group card | pending-claim banner, "You owe" + Settle up, group spend / your share / you fronted, Who-owes-who bilateral rows, expense list with day headers and All / Unsettled / You-paid filters |
| Expense detail (sheet) | tap an expense row | total, paid-by, split breakdown with per-person amounts, note, delete, Done |
| Settle up (sheet) | Settle up button | one row per counterparty with Pay, pending claims with Review, per-row Undo after Pay, "Done for now" |
| Approval (dialog) | Review on a claim | "Did X pay you?" — amount, receipt slot, claimant's message, Not yet / Yes-paid-me |
| Add expense step 1 "How much?" (sheet) | + in the tab bar | amount keypad, category chips, note field; Next disabled until an amount exists |
| Add expense step 2 "Who split it?" | Next from step 1 | payer picker, participant toggles, Evenly / Custom, live per-person shares, Save split |
| Activity | Activity tab | cross-group expense feed |
| Friends / You | Friends tab; You tab | one profile screen under two titles: you, friends list, settings, sign out |

## Edges that behave in a way step 9 must decide about

- **Back returns to where you came from**, not to a fixed parent: entering a group from the You
  tab and pressing Back lands on the You tab. Stack navigation, not hierarchy navigation.
- **The + button hardcodes a group.** The prototype drops you straight into the Osaka trip; the
  real app needs a group chooser when adding from a global tab (from inside a group, the group is
  known).
- **Pay is optimistic with Undo** in the prototype. The real flow is a payback claim that stays
  PENDING until the person owed (or the trip creator) approves — the Undo affordance maps to
  deleting your own pending claim, not to reversing an approved one.
- The bilateral rows on the group screen already obey invariant 2 in the prototype's numbers:
  39.10 + 46.00 − 42.30 = 42.80, the header's "You owe".

## Known prototype artefacts (not bugs to fix)

- Babel-standalone console warning: it compiles its JSX in the browser.
- `GET /.image-slots.state.json` 404s: its screenshot-slot tooling looks for saved state.
- The split arithmetic drops cents, as above — the negative control for our port.
