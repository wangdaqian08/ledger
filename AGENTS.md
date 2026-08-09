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
person owed has not agreed to must leave every balance untouched. `TripSnapshot` therefore hands
the engine *every* payback and lets it do the filtering — passing only the approved ones would
work today and hide the rule the moment anything here had to reason about a pending claim.

**`POST /api/trips/{id}/remind` delivers nothing, on purpose.** Push notifications are out of
phase 1 (§9), so it validates that the nudge makes sense and returns. Do not make it "work" by
inventing a channel, and do not let any response imply something was sent.

**Approval is the person owed, or the trip's creator.** Never the person paying. §3 and §7a once
said "only the person owed" while §5 added the creator; that was settled in favour of §5, and all
three now say so. The creator being able to settle a debt between two other people is the accepted
cost of a trip not stalling when somebody stops answering.

**Flyway owns the schema; Hibernate only checks it.** `ddl-auto` is `validate` and must stay that
way. Schema changes are a new `V__` migration — never an edit to `V1__init.sql`, which has already
run everywhere. `SPRING_SESSION` is in that migration too, which is why
`spring.session.jdbc.initialize-schema` is `never`: two owners of one schema is how you get a
Flyway validation failure at the worst possible moment.

**No share amount is ever stored.** `item_shares` holds the people list and the *inputs* to the
split (`weight`, `exact_amount_minor`). Everything else — `owed`, `net`, `ALL_SQUARE` — is derived
on read by `engine`. Caching a computed total in a column would reintroduce exactly the stale-number
bug the whole design exists to avoid.

**The split algorithm exists twice, and the two are pinned together.** `web/src/lib/split.ts` is a
deliberate port of `Split.kt`, because the SplitBar shows each person's amount live while it is
dragged and that means predicting largest remainder — spare cents included — before anything is
saved. `engine/src/test/resources/split-vectors.json` is the contract: `SplitVectorsTest`
regenerates and checks it, `web/tests/split.spec.ts` checks the port against the same file. Change
one implementation without the other and a test goes red. Change both without reading the vector
diff and every existing item's rounding has quietly moved.

**A test that asserts exact cents must pin the item id.** The salt is the id, so with a
server-generated one the tie-break is redrawn on every run and any assertion about *which* person
carries the spare cent is a coin toss. `HotelScenarioApiTest` learned this the hard way — it passed
locally for two steps and then failed in CI on a bound that was wrong by one cent. Pin the id, then
assert exactly.

**The client mints an item's id.** That is what makes the preview exact — the salt is the id, so it
has to exist before the split can be shown. `POST /api/trips/{id}/items` takes an optional `id` and
returns 200 instead of 201 when it has seen that id before, so a retry on a flaky connection cannot
double an expense.

**`engineItemId` must fold both halves of the UUID.** It is the item's identity inside the engine
as well as its split salt, so two ids that map to the same value become one item: one bill's
paybacks get counted against the other and a bill nobody paid reads ALL_SQUARE. It once used only
`mostSignificantBits`, which is fine for random v4 ids and wrong for UUIDv7 — that carries a
millisecond timestamp in the high bits, so two expenses added in the same moment collide by
construction, and the client is what mints these ids. `ItemApiTest` holds this with ids differing
only in their low bits.

The mapping is also permanent once anything is deployed: change it and every existing item
redistributes a cent between different people, silently, with no migration that could put it
back.

**`spring.mvc.problemdetails.enabled` must stay on.** Without it Spring answers with its legacy
error body, which omits the message — every reason attached to a `ResponseStatusException` is
discarded and the client gets a bare status it cannot explain. `ItemApiTest` asserts a 400 still
carries its numbers.

**One trip cannot reach into another.** Every foreign key touching a member or an item is composite
and carries `trip_id`, pointing at the `UNIQUE (trip_id, id)` constraints on `trip_members` and
`items`. Do not "simplify" one back to `REFERENCES trip_members (id)` — that permits a member of
trip B on trip A's item, which hands them a share of money they are not part of and breaks
invariant 1 on both trips at once. `TripScopingTest` holds this, positive control included.

## The two invariants

If a change breaks either of these, the change is wrong — not the test.

1. **Balances sum to zero.** Across every member of a trip, `netMinor` totals exactly 0.
2. **Settle-up rows sum to a person's overall position.** For any member, their `owesBetween`
   figures against everyone else sum to `−netMinor`. If this breaks, the Settle-up screen
   silently lies about who owes what, which is the one failure the app cannot survive. Asserted
   over HTTP in `SettlementApiTest` as well as property-tested in the engine — the engine holding
   it is no use if the adapter loses it on the way out.

Both are property-tested over hundreds of randomly generated trips, not just examples. When you
add a rule, add the property, not only the case that prompted it.

## Layout

```
engine/                pure Kotlin business rules: split, settle, item state
server/                Spring Boot 4 + Flyway + Postgres: auth, trips, items, paybacks, settle-up
web/                   Vue 3 + TS + Vite. The design system is ported; screens are step 9
docs/specs/            the design spec — source of truth
Tally_Design_System/   vendored design reference. See below.
```

Run the whole thing with seeded demo data — the hotel scenario, mid-story — with
`./gradlew :server:bootTestRun`. It starts a real Postgres through Testcontainers, so it needs
Docker and uses the same migration as production. H2 was measured and rejected: it refuses fourteen
statements of `V1__init.sql`, and the blocking one is that it has no partial indexes at all,
including the two that stop a trip having two categories called "Food". A demo on a weaker schema
is a demo of a different application. `docs/demo/tally-demo.html` is the original click-through
prototype, kept for navigating the intended screens quickly.

`web/` is a plain npm project, **not** a Gradle module, so `./gradlew check` does not touch it and
never will. That is deliberate: the JVM build stays fast and JVM-only, and §10's `npm --prefix web`
commands are the real ones rather than a second way of doing the same thing. CI runs both.

Inside `server/`, `identity/` is the one seam to whoever vouches for a user — `MockIdentityProvider`
on the dev profile today, Google at build order step 10. Nothing above that interface knows which is
in play, and nothing should learn.

**Server packages are by feature, never by layer, and a file is named for exactly what it holds:**
`XController.kt` holds only the controller, `XService.kt` only the service, `XCommands.kt` and
`XViews.kt` a feature's request and response types — and a type standing alone takes its own name
(`SignInRequest.kt`), which the ktlint filename rule demands anyway. There is no bundle file: an
`XApi.kt` holding types + controller + service was tried and retired, because a tree listing that
hides where a controller lives misleads whoever reads it, and "small enough to bundle" is a
judgment call that drifts — `settlement/` had already straddled it. A `controllers/`–`models/`
re-sort of the packages themselves would group by kind what changes together by feature — that
debate is settled too, do not reopen either silently.

**A trip you cannot see returns 404, not 403.** A stranger must not be able to confirm that a trip
exists. 403 is reserved for people who *are* on the trip but lack the right — a member who is not
the creator trying to change the roster.

**There is no default invite-signing secret, and there must never be one.** `application-dev.yaml`
supplies one for local work and the tests; every other profile must be given one or the application
refuses to start. A default committed here is a signing key that is public the moment the repository
is, and "we'll override it in production" is not a mechanism.

**Kotlin Boolean properties named `isX` reach the wire as `x`.** Jackson strips the prefix. Anything
in a `*View` that starts with `is` needs `@get:JsonProperty` pinning the name, or the client silently
reads a field that is not there — `MemberView.isYou` is the worked example.

`SecurityConfig` publishes the `CsrfTokenRepository` and `CsrfTokenRequestHandler` as beans because
`AuthController` rotates the token at sign-in and the filter chain validates against it. Two
instances would be two opinions about where the token lives. The handler in particular must be the
eager one everywhere: rotation clears the old cookie first, and a deferred handler would decline to
write the replacement, leaving the browser with no token and every write rejected.

`Tally_Design_System/` is **reference, not app code.** The `.jsx` files are a vendored React
design system used to read tokens, spacing and component behaviour from. Do not edit them, do not
import them, and do not ship them. The frontend is Vue 3; components get ported, not reused.

**Read the reference for behaviour and layout, never for arithmetic.** Three components make the
point, and all three were rebuilt on whole cents:

- `Amount` takes a major-unit float and calls `toLocaleString`. `AmountText.vue` takes integer
  minor units and never computes a fraction — `money.ts` splits with `%` and an exact division.
- `SplitBar` keeps float percentages and multiplies them back into amounts. `SplitBar.vue` keeps
  integer weights and asks `split.ts`, which is pinned to the engine.
- `GroupCard` calls a balance settled when `Math.abs(balance) < 0.005`. Ours is `=== 0`, because a
  tolerance means telling somebody they are square while they still owe four cents.

The money rule does not stop at the API boundary. A faithful port would have broken it at the very
last step, where nobody looks.

Gradle modules are added to `settings.gradle.kts` as they are built; `engine` and `server` are
there, and `web` never will be.

## Verifying

```bash
./gradlew :engine:test    # 64 tests, about a second
./gradlew :server:test    # real Postgres via Testcontainers — needs a running Docker daemon
./gradlew spotlessCheck   # Kotlin formatting
./gradlew spotlessApply   # fix Kotlin formatting
./gradlew check           # all of the JVM side

npm --prefix web test     # Vitest
npm --prefix web run lint # ESLint + Prettier
npm --prefix web run build  # typechecks with vue-tsc, then bundles
npm --prefix web run e2e  # Playwright drives the real app against the seeded backend — needs Docker;
                          # boots the server (Testcontainers) and Vite itself, so nothing else
                          # should be holding ports 8080 or 5173
```

CI (`.github/workflows/ci.yml`) runs five parallel jobs — formatting, engine, server, web unit,
and the Playwright e2e — behind a single aggregate check, `CI green`, which is the one branch
protection requires. Every push to `main` and every PR. Needs JDK 25; Gradle comes from the
wrapper.

`server` tests use Testcontainers, never H2. The CHECK constraints, the partial unique indexes and
Hibernate's schema validation are precisely what an in-memory substitute would silently not have,
so a green suite against a fake database would prove nothing. If Docker is not running, start it —
do not swap the database out to make the tests run.

The container is started once in `PostgresTest`'s companion and deliberately carries **no**
`@Testcontainers` annotation: that extension stops the container when its own test class finishes,
which leaves every later class talking to a dead database.

## Formatting

ktlint 1.8.0 via Spotless. Several standard rules are **deliberately disabled** in
`.editorconfig`, with the reasoning written there. The short version: the engine tests are the
executable form of the acceptance scenarios and are meant to be read, so the rules that would
collapse their aligned trailing comments or move their explanatory comments off the arguments
they explain are off.

Do not re-enable those rules to make a diff tidier, and do not hand-reformat the aligned comment
columns in `HotelScenarioTest.kt`. If ktlint and the house style disagree somewhere new, that is
a conversation, not a silent reformat.
