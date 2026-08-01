# Ledger

A mobile web app for splitting costs on group trips.

## The problem it solves

A hotel deposit gets paid when **10** people are coming. The balance gets paid when **13** are
coming. On arrival there are **14** — Jack turned up at check-in and hasn't paid a cent.

The bill never changed. Everyone's fair share changed three times. The ten who already paid are
now **owed money back**, and Jack owes a full share. Working that out by hand is where group
trips go wrong.

Ledger's answer is that the arithmetic is never the hard part — keeping each cost's people list
correct is. So there is one primitive, and every number is derived from it live:

| | what it is |
|---|---|
| **Item** | money that left the group, paid by one person, with an editable list of who shares it |
| **Payback** | money handed back, pending until the person owed approves it |

Correcting the two hotel items' people lists to 14 is the entire fix: `$1,000/14 + $1,000/14`
is `$2,000/14`. No deposit-stage concept, no rebalancing pass.

## Layout

```
engine/                pure Kotlin. Split, settle, item state. No Spring, no database.
docs/specs/            the design spec — read this first
Tally_Design_System/   the design system: tokens, components, and a click-through demo
server/                not built yet
web/                   not built yet
```

`engine/` deliberately has no production dependencies. That is what lets 2,000 randomly
generated trips be verified in about a second.

## Running it

Needs JDK 25. Gradle comes from the wrapper.

```bash
./gradlew :engine:test          # 51 tests
```

## Status

| | |
|---|---|
| Design spec | done — `docs/specs/2026-07-31-ledger-design.md` |
| Engine | done — 51 tests, including the hotel scenario above |
| Server | not started |
| Web | not started |

Backend is Kotlin on Spring Boot 4 with Postgres. Frontend will be **Vue 3** — the `.jsx` files
under `Tally_Design_System/` are the vendored design system used as reference, not app code.

## How the maths is kept honest

- All money is `Long` minor units. No `Double`, no `BigDecimal`, anywhere.
- Splits use largest remainder, so parts sum to the total exactly. A salt rotates which people
  absorb the spare cents, so the first name on the list doesn't pay them on every item.
- Two properties are tested rather than assumed: **everyone's balances sum to zero**, and
  **each person's settle-up rows sum to their overall position**. If the second breaks, the
  Settle-up screen silently lies.
- Tests are mutation-checked. Breaking the split rule, the approval rule or the rounding each
  has to make the suite fail, or the test wasn't worth writing.
