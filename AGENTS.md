# Working on Ledger

`CLAUDE.md` is a symlink to this file. Edit this one.

`docs/specs/2026-07-31-ledger-design.md` is the source of truth. It is not background reading —
the acceptance scenarios in §2 exist verbatim as tests, and §8 is the build order. Read it before
changing behaviour, and update it in the same commit when behaviour changes.

## Rules that are not negotiable

**Money is `Long` minor units.** Every amount in the codebase is named `*Minor` and is an integer
number of cents. There is no `Double` and no `BigDecimal` anywhere, and adding one is a bug even
if the tests pass. Floating point cannot represent a cent, and this app's entire value is that
its arithmetic is exactly right.

**`engine/` has zero production dependencies.** Not Spring, not Jackson, not a JSON library, not a
logging framework. Check `engine/build.gradle.kts`: everything is `testImplementation`. This is
what lets 2,000 randomly generated trips be verified in about a second, and it is the reason the
business rules can be trusted independently of any framework. Persistence, serialisation and HTTP
belong in `server/`.

**Splits use largest remainder.** `splitByWeight` in `Split.kt` floors every share, then hands the
leftover cents to the largest fractional parts. Parts sum to the total exactly — never a cent over
or under. Ties break on position rotated by a salt (the item's id), so the spare cent lands on
different people across a trip rather than always the first name on the list. Do not replace this
with rounding.

**Only approved paybacks count.** A `Payback` with `PaybackStatus.PENDING` moves no number
anywhere. `settle`, `itemState` and `owesBetween` all filter to `APPROVED` first. A claim the
person owed has not agreed to must leave every balance untouched.

## The two invariants

If a change breaks either of these, the change is wrong — not the test.

1. **Balances sum to zero.** Across every member of a trip, `netMinor` totals exactly 0.
2. **Settle-up rows sum to a person's overall position.** For any member, their `owesBetween`
   figures against everyone else sum to `−netMinor`. If this breaks, the Settle-up screen
   silently lies about who owes what, which is the one failure the app cannot survive.

Both are property-tested over hundreds of randomly generated trips, not just examples. When you
add a rule, add the property, not only the case that prompted it.

## Layout

```
engine/                pure Kotlin business rules: split, settle, item state
docs/specs/            the design spec — source of truth
Tally_Design_System/   vendored design reference. See below.
server/                not built yet — Spring Boot 4 + Flyway + Postgres (build order step 3)
web/                   not built yet — Vue 3 (build order step 8)
```

`Tally_Design_System/` is **reference, not app code.** The `.jsx` files are a vendored React
design system used to read tokens, spacing and component behaviour from. Do not edit them, do not
import them, and do not ship them. The frontend is Vue 3; components get ported, not reused.

Modules are added to `settings.gradle.kts` as they are built. `server` and `web` do not exist yet,
so do not write code that imports them or CI that builds them.

## Verifying

```bash
./gradlew :engine:test    # 51 tests, about a second
./gradlew spotlessCheck   # formatting
./gradlew spotlessApply   # fix formatting
./gradlew check           # both
```

CI (`.github/workflows/ci.yml`) runs `spotlessCheck` then `:engine:test` on every push to `main`
and every PR. Needs JDK 25; Gradle comes from the wrapper.

## Formatting

ktlint 1.8.0 via Spotless. Several standard rules are **deliberately disabled** in
`.editorconfig`, with the reasoning written there. The short version: the engine tests are the
executable form of the acceptance scenarios and are meant to be read, so the rules that would
collapse their aligned trailing comments or move their explanatory comments off the arguments
they explain are off.

Do not re-enable those rules to make a diff tidier, and do not hand-reformat the aligned comment
columns in `HotelScenarioTest.kt`. If ktlint and the house style disagree somewhere new, that is
a conversation, not a silent reformat.
