Tappable pill — the workhorse of Tally's low-input philosophy. Prefer a row of chips over a select or a text field.

```jsx
<Chip icon="pizza" selected>Food</Chip>
<Chip icon="car">Transport</Chip>
```

Unselected chips are white with a hairline border; selected chips fill and lift. Lay rows out with flex + `gap: var(--gap-inline)` and allow horizontal scroll rather than wrapping to three lines.
