The Settle Up screen's core row — one per person you're not square with.

```jsx
<BalanceRow name="Mei Wong" hue={4} amount={-18.4} onSettle={pay} />
```

At this density the name is allowed to wrap to two lines rather than truncate — never clip the name of the person you owe money to. If the row also carries a caption or a wide action, pass `size="md"` so the figure gives the name room.

The action label flips with the sign: you owe them → "Pay" (mint), they owe you → "Remind" (soft action). At `amount={0}` the action disappears and the figure greys out.
