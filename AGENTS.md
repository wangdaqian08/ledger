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

**The client mints an item's id.** That is what makes the preview exact — the salt is the id, so it
has to exist before the split can be shown. `POST /api/trips/{id}/items` takes an optional `id` and
returns 200 instead of 201 when it has seen that id before, so a retry on a flaky connection cannot
double an expense.

**The item salt mapping is permanent.** `engineItemId` in `TripSnapshot.kt` turns an item's UUID
into the `ItemId` the engine rotates its largest-remainder tie-break on. Change it and every
existing item redistributes a cent between different people, silently, with no migration that could
put it back. It is a pure function of the item's identity and must stay one.

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
web/                   Vue 3 + TS + Vite. Tokens and core components so far; screens are step 9
docs/specs/            the design spec — source of truth
Tally_Design_System/   vendored design reference. See below.
```

`web/` is a plain npm project, **not** a Gradle module, so `./gradlew check` does not touch it and
never will. That is deliberate: the JVM build stays fast and JVM-only, and §10's `npm --prefix web`
commands are the real ones rather than a second way of doing the same thing. CI runs both.

Inside `server/`, `identity/` is the one seam to whoever vouches for a user — `MockIdentityProvider`
on the dev profile today, Google at build order step 10. Nothing above that interface knows which is
in play, and nothing should learn.

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

**Porting is not copying, and `Amount` is the worked example.** Tally's version takes a major-unit
float and calls `toLocaleString`. `AmountText.vue` takes integer minor units and never computes a
fraction — `web/src/lib/money.ts` splits with `%` and an exact division instead of `/ 100`. The
money rule does not stop at the API boundary, and a faithful port would have broken it at the very
last step, where nobody looks.

Gradle modules are added to `settings.gradle.kts` as they are built; `engine` and `server` are
there, and `web` never will be.

## Verifying

```bash
./gradlew :engine:test    # 51 tests, about a second
./gradlew :server:test    # real Postgres via Testcontainers — needs a running Docker daemon
./gradlew spotlessCheck   # Kotlin formatting
./gradlew spotlessApply   # fix Kotlin formatting
./gradlew check           # all of the JVM side

npm --prefix web test     # Vitest
npm --prefix web run lint # ESLint + Prettier
npm --prefix web run build  # typechecks with vue-tsc, then bundles
```

CI (`.github/workflows/ci.yml`) runs `spotlessCheck`, then both suites, on every push to `main` and
every PR. Needs JDK 25; Gradle comes from the wrapper.

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
