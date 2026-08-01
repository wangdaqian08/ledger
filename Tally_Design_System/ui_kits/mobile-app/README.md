# Tally — mobile web app UI kit

A click-through recreation of the Tally split-bill app as it runs in mobile Safari.
Everything is composed from the design system's own components — no screen re-implements a primitive.

## Files

| File | What it holds |
|---|---|
| `index.html` | The iOS Safari frame (status bar, address bar, home indicator) and the mount |
| `data.js` | `window.TallyData` — three sample groups, members, expenses and balances |
| `Screens.jsx` | `GroupsHome`, `OverallScreen`, `GroupDetail`, `ExpenseDetailSheet`, `Activity`, `You`, `AddExpenseSheet`, `SettleUpSheet` |
| `App.jsx` | `TallyApp` — nav state, sheet state, toasts, save/settle mutations |

## What you can do in it

1. **Groups home** — overall balance card, per-group tiles with member stacks.
2. Tap the **overall balance card** → **Overall**: split of what you owe vs what you're owed, every person's net across all groups (with which groups each debt comes from), a by-group breakdown that jumps into any group, and how much of the total spend you fronted.
3. Tap a group → **group detail**: balance hero, three-up spend stats (group spend / your share / you fronted), who-owes-who rows, member and currency summary, filter chips (All / Unsettled / You paid), and the expense feed grouped by day.
4. Tap any expense → **expense detail** sheet: total and your position side by side, who fronted the bill, a read-only SplitBar with per-person share rows and percentages, the note, and delete.
5. Tap the raised **+** in the tab bar → **add expense** sheet: keypad amount → category chips → who paid → tap avatars to include/exclude → **drag the SplitBar handles** to move money between people → save. The new expense appears at the top of the feed and a toast confirms.
6. Tap **Settle up** → mark each person paid. A marked row stays in place, greys out with a struck-through name, and carries its own **Undo** — plus an "Undo <name>" button in the footer for the last one marked and a **Reset all** chip once two or more are marked. Marking the wrong person is always a one-tap mistake. When all are marked the CTA turns mint and the group flips to the all-square state with the celebration banner.
7. Tab bar switches between Groups / Activity / Friends / You.

## Notes

- Screen width is 390px (iPhone 14/15 logical width). All hit targets are ≥ 44px, most are 48px.
- The frame chrome is decorative; it is not a real browser.
- Balances update with deliberately simple arithmetic — this is a visual kit, not a ledger engine.
