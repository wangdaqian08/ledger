The primary action in any Tally screen — a colored slab with a hard ink edge that physically sinks 3px when pressed.

```jsx
<Button tone="action" size="lg" block icon="plus">Add expense</Button>
<Button variant="outline">Cancel</Button>
<Button variant="solid" tone="mint" icon="check">Settle up</Button>
```

One primary slab per screen. `tone="mint"` for settling/positive money, `tone="coral"` for destructive, `tone="lemon"` for celebratory secondary. `variant="ghost"` for inline text actions. Never smaller than `size="sm"` (40px) and prefer `md` (48px) on touch surfaces.
