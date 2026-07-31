# Tally — Design System

A design system for **Tally**, a mobile-web ledger for splitting bills with friends.

> **Naming note.** The attached repository is called `ledger` and its README reads only "web based ledger book for split bill". No product name, logo or brand was supplied, so **Tally** is a working name chosen for this system. Rename it and the palette/type stay valid.

---

## 1. Context & sources

**The product.** One thing: a group of friends spends money together, and Tally keeps track of who fronted what and who owes whom. Mobile web only — iOS Safari and Chrome on Android. Not a payments app: Tally never moves money, it only records and resolves the arithmetic.

**Product surfaces.** Exactly one: the mobile web app (`ui_kits/mobile-app/`). No marketing site, docs site, dashboard or admin surface exists or was specified, so none was invented.

**Design principles taken from the brief.**
1. **Simple and colourful.** Colour is the primary information carrier, not decoration.
2. **Obvious.** No hidden gestures, no ambiguous icons without labels, no modal ladders.
3. **Tap and drag, don't type.** Chips, steppers, avatar toggles and a draggable split bar replace almost every text field. The only keyboard is Tally's own numeric keypad.
4. **Animated.** Everything that changes moves, on one spring curve.

### Sources given, and what was in them

| Source | Status |
|---|---|
| `https://github.com/wangdaqian08/ledger` (branch `main`) | **Read in full. Effectively empty** — only `README.md` (2 lines), `LICENSE`, `.gitignore`. No code, styles, components or assets. |
| Local mounted folder `ledger/` | Same three files as the repo. |
| `uploads/DESIGN.md` | **Not used.** It is a design analysis of Apple's website (SF Pro, Action Blue `#0066cc`, Apple product tiles and store configurator) — another company's brand, and unrelated to bill splitting. Reproducing it was declined. It did inform the *documentation structure* of this readme, nothing visual. |
| Figma | None provided. |

**Consequence:** this system is an **original design**, not a recreation. Every colour, type choice, component and screen here was authored for this project. If real Tally/ledger source or Figma exists, attach it and this system should be re-derived from it — a recreation grounded in real code will always beat an invention. Explore the repository above (and any successor) for that purpose.

**No logo exists.** None was provided, and none was drawn. Wherever a mark would go, the wordmark **Tally** is set in Figtree 900 with `-0.05em` tracking (see `thumbnail.html`). `assets/` contains no logo file for this reason.

---

## 2. Content fundamentals

Money between friends is socially awkward. Tally's copy exists to defuse that: it states facts plainly, never implies blame, and never nags.

**Rules**

- **Sentence case everywhere.** "Add expense", "Settle up", "Who owes who". Title Case is never used. `UPPERCASE` appears only in 12px micro-labels (`--text-label`) and `Badge`.
- **Second person, and only when the user is a party to it.** "You owe $18.40", "You get back $12.50", "You're all square". Never "The user", never "we" for the product.
- **Third person for other people, by first name.** "Mei paid", "Sam owes you". Full names appear only in `BalanceRow` and the friends list.
- **No jargon, no accounting words.** Never *reconcile*, *transaction*, *settlement request*, *reimbursement*, *net position*.
- **Short lines.** Row subtitles are ≤ 4 words. Sheet body copy is ≤ 2 sentences.
- **The maths is stated, not explained.** "$18.40" with the word "you owe" beneath it. No "Your calculated share of this expense based on an even split is…".
- **Never scold or dun.** The action on a balance you're owed is "Remind" or "Nudge" — not "Chase", "Request payment", or "Overdue".
- **Zero is a feeling, not a number.** A settled group shows "All square", never `$0.00`.
- **No emoji anywhere.** Categories are Lucide glyphs. This is deliberate: emoji render differently per platform and this is a money product.

**Examples**

| Say | Not |
|---|---|
| You're all square | Balance reconciled: $0.00 |
| Mei paid · Yesterday | Transaction initiated by M. Wong |
| Nudge Sam | Send payment reminder notification |
| Split it, sorted | Effortless expense management for groups |
| Add the first expense and Tally will do the maths. | No expenses found in this group. |
| Tally doesn't move money. Mark what you've squared up in real life and everyone's balances follow. | Settlement is recorded off-platform. |

**Numbers.** Always two decimals, always with a currency glyph, always in `--font-money` with tabular figures. Signs are shown with `+` / `−` (U+2212 minus, not a hyphen) only when the sign is the point.

---

## 3. Visual foundations

### Colour

Warm paper base, soft near-black ink, one action colour, and a semantic money pair.

- **Paper** `#FFFBF2` is the app background — a warm off-white, never pure white. Pure white `#FFFFFF` is reserved for cards, so cards separate from the page without a shadow.
- **Ink** `#1A1720` is text *and* the border of nearly every surface. It's a violet-leaning near-black so it sits with Grape rather than fighting it.
- **Grape** `#4B3BFF` is the only action colour. Every button, every active tab, every focus ring. There is no second brand colour.
- **Lemon** `#FFCE2E` is the accent — empty-state glyphs, "Undo", the occasional celebratory slab. Never an action.
- **Mint** `#00BF87` = money coming to you. **Coral** `#FF5147` = money you owe. **Ink-3** `#7C7689` = settled. These three are never used decoratively; if something is mint it means a positive balance.
- **Person hues** `--person-1…8` (coral, amber, lemon, mint, sky, grape, orchid, pink) are assigned round-robin as people join a group and never change. The same person is the same colour in their avatar, their SplitBar segment, and their share label. This is the system's single strongest idea.
- Category discs also draw from the person ramp, so the whole app lives in one palette — no separate category colour set.

### Type

- **Figtree** (`--font-core`) for everything: 400 / 500 / 700 / 800. Friendly geometric sans, generous x-height, reads cleanly at 13px on a phone.
- **Space Grotesk** (`--font-money`) for every currency figure and for token values in documentation. Tabular figures so columns of amounts align.
- Weight 800 for headlines and row titles, 700 for buttons and emphasis, 500 for body, 400 rarely.
- Negative tracking scales with size: `-0.03em` at 40px, `-0.02em` at 24px, none below 16px.
- Sizes: hero 40 · display 32 · title 24 · heading 19/16 · body 17/15 · caption 13 · label 12. Money: 48 / 28 / 17 / 14. **Nothing below 12px ships.**

**Font substitution flag:** no font binaries were supplied. Both families are Google Fonts loaded over CDN in `tokens/fonts.css` (so the compiler reports 0 self-hosted `@font-face` rules). If Tally licenses display faces, drop the binaries in `assets/fonts/` and replace the `@import` with real `@font-face` rules.

### Backgrounds & imagery

No photography, no illustration, no gradients, no patterns, no grain, no noise. The page is flat warm paper. Depth comes from borders and offsets only. There are no full-bleed images anywhere in the product, and none were supplied to copy.

### The slab: borders and shadows

The defining motif. Every surface is a **slab**: 2px solid ink border, generous radius, and a hard ink edge offset **downward** (`0 4px 0 0`) with no blur. Nothing in Tally has a soft grey drop shadow.

- `--slab-1` 2px (list cards) · `--slab-2` 4px (hero cards, buttons) · `--slab-3` 6px (the top-of-screen balance card).
- Coloured slabs take their own darker press tone as the edge (`--slab-action`, `--slab-mint`) instead of ink.
- **Three soft shadows exist**, and only for layers genuinely floating over content: `--lift-sheet`, `--lift-drag` (an element under the finger), `--lift-toast`.

### Press, hover and focus

- **Press:** a slab sinks — `translateY(3px)` while its edge shrinks 4px → 1px, over 90ms. It reads as physically pushing a key.
- Bare glyph buttons have no edge, so they **scale to 0.90** instead.
- **Hover is not designed.** This is a touch product; `ListRow` gets a faint `--surface-hover` wash and that's all. Don't add hover-only affordances.
- **Focus:** 3px solid Grape outline, 2px offset. Never removed.
- **Disabled:** 42% opacity, edge retained, no colour change.

### Motion

One curve does almost everything: `--ease-spring` = `cubic-bezier(.34, 1.56, .64, 1)` — a real overshoot.

| Duration | Used for |
|---|---|
| 90ms `--dur-instant` | press feedback (eased out, not sprung) |
| 160ms `--dur-fast` | chips, avatar toggles, tab icons |
| 240ms `--dur-base` | cards, list reflow, toasts, amount bumps |
| 380ms `--dur-slow` | bottom sheets, screen changes |
| 620ms `--dur-celebrate` | the all-square pop |

Rules: sheets **spring in, ease out**. SplitBar segments follow the finger 1:1 with no transition while dragging, then animate on the spring when set programmatically. The amount readout scales 0.94 → 1 on every keystroke. `prefers-reduced-motion` collapses every duration to 1ms.

### Transparency & blur

Used exactly once: `AppBar` sits on `paper` at 88% with `saturate(180%) blur(14px)` so content scrolls under it. Sheet scrims are `rgba(26,23,32,0.44)`. Nothing else is translucent — no frosted cards, no glassmorphism.

### Corner radii

6 xs · 10 sm · 14 md (inputs, keypad keys, category discs) · 20 lg (**cards — the default**) · 28 xl (sheets) · pill (buttons, chips, badges, progress, toasts). Buttons and chips are *always* pills; cards are *never* pills. Don't invent values between these.

### Layout

- Single column, 390px design width, `--gutter-screen` 16px page margins.
- Fixed elements: `AppBar` sticky top (56px), `TabBar` sticky bottom (64px) with a raised 60px centre action, sheets absolute to the screen frame, toasts above the tab bar.
- 4px spacing base; structural rhythm is 12/16/20/24, sections separated by 28px.
- **Minimum hit target 48px** (`--tap-min`), never below 44px. `env(safe-area-inset-bottom)` is respected at the tab bar and sheet footers.
- Horizontal scroll (chips, avatar rows) is preferred over wrapping to a third line.

### Card anatomy

White `--surface-card`, 2px ink border, 20px radius, 16px padding, 4px ink edge. Lists live in an *unpadded* card with 1.5px `--border-soft` hairlines between rows and `divider={false}` on the last. Tinted cards (mint / coral / lemon / sunk / ink) carry meaning, not decoration. Nest at most one level, and the inner card drops to `lift={0}` `tone="sunk"`.

### Dark mode

Not designed. Light only. `tokens/colors.css` is structured with semantic aliases so a `[data-theme="dark"]` scope can be added later without touching components.

---

## 4. Iconography

- **System: Lucide** (`lucide-static@0.446.0`), 24px grid, 2px round-cap round-join stroke. It matches the friendly-but-plain tone and has every glyph the product needs.
- **Substitution flag:** the sources contained **no icons of any kind**, so Lucide is a chosen substitute, not a recreation.
- **Vendored locally.** The 38 glyphs the product uses live in `assets/icons/*.svg`, copied from `github.com/lucide-icons/lucide` (ISC), with their markup inlined into `components/core/icon-paths.js`. `Icon` renders a real inline `<svg>` — no network request, correct `currentColor` inheritance, and glyphs survive screenshot / PDF / PPTX export. Adding a glyph means adding it to both places; `ICON_NAMES` is the canonical list, and an unknown name renders a dashed-circle placeholder rather than a black box.
- **Always via the `Icon` component.** It mask-renders the SVG so the glyph inherits `color` — never hand-write an SVG path, never use an icon font, never use a PNG icon.
- Sizes: 16 inline · 18–20 in rows and buttons · 22 in category discs · 24 in app bars and tab bars · 30 in the centre action · 34 in empty states.
- **No emoji, ever** — not in UI, not in categories, not in copy.
- **No unicode characters as icons**, with two exceptions that are typography rather than iconography: `−` (U+2212) for negative amounts and `×` as a share multiplier suffix.
- **The eight canonical category glyphs** (exported as `CATEGORIES` from `CategoryPicker`): `utensils` Food · `beer` Drinks · `car-front` Transport · `bed-double` Stay · `shopping-basket` Groceries · `ticket` Fun · `house` Home · `circle-dashed` Other.
- Group glyphs are free-form Lucide slugs (`plane`, `house`, `coffee`, `users`).

---

## 5. Index

### Root

| File | What it is |
|---|---|
| `styles.css` | The single entry point consumers link. `@import` list only. |
| `readme.md` | This document. |
| `SKILL.md` | Agent-skill wrapper so this folder works as a Claude Code skill. |
| `github.md` | Source-repo association and sync record. |
| `thumbnail.html` | Homepage tile — Tally wordmark on Grape with the accent strip. |
| `tokens/` | `fonts` · `colors` · `typography` · `spacing` · `radius` · `elevation` · `motion` · `base` |
| `guidelines/` | 17 foundation specimen cards (Colors, Type, Spacing, Brand). |
| `components/` | Reusable primitives, grouped by concern. |
| `ui_kits/mobile-app/` | The click-through app recreation. |
| `templates/tally-app/` | Starting-point template: a Tally mobile screen consuming projects can copy. |
| `assets/icons/` | 38 vendored Lucide SVGs. No logo or imagery — see `assets/README.md`. |

### Components

**`components/core/`** — `Button`, `IconButton`, `Card`, `Badge`, `Chip`, `Avatar`, `AvatarStack`, `Amount`, `Icon`

**`components/forms/`** — `Input`, `AmountInput`, `Keypad`, `Stepper`, `SplitBar`, `PersonToggleRow`, `CategoryPicker`

**`components/lists/`** — `ListRow`, `ExpenseRow`, `BalanceRow`, `GroupCard`

**`components/navigation/`** — `AppBar`, `TabBar`, `Sheet`

**`components/feedback/`** — `Toast`, `ProgressBar`, `EmptyState`, `SettledBanner`

Each has a sibling `.d.ts` props contract and a `.prompt.md` usage note.

#### Intentional additions

No source defined a component inventory, so this is an authored set sized to the product rather than a recreation. Three entries are worth calling out as deliberate rather than conventional:

- **`Icon`** — a wrapper over the vendored Lucide set, so no screen ever hand-writes an SVG.
- **`SplitBar`** — the product's signature drag interaction; the reason the brief's "draggable, less input" requirement has a home.
- **`Keypad` + `AmountInput`** — Tally never opens the OS keyboard for money, so the pad is a first-class component.

Standard primitives the brief did **not** need were left out on purpose: no Select, Checkbox, Radio, Switch, Tabs, Tooltip, Dialog or Accordion. Chips, avatar toggles and sheets cover those jobs, and adding unused primitives would invite designs the product doesn't have.

### UI kits

| Kit | Screens |
|---|---|
| `ui_kits/mobile-app/` | Groups home · Group detail · Activity · You / friends / settings · Add-expense sheet (keypad → categories → who paid → who's in → drag split) · Settle-up sheet with the all-square celebration |

### Not included

- **No slide templates.** No deck or slide source was provided, so none was invented.
- **No marketing site kit.** No such surface exists in the sources.
- **No dark theme, no error/validation states beyond `Input error`, no localisation or multi-currency work.**
