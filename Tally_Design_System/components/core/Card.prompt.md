Every grouped surface in Tally. Hard 2px ink border, 20px radius, downward ink edge — no blurry shadows.

```jsx
<Card lift={4} tone="mint"><Amount value={42.5} tone="owed" size="lg" /></Card>
<Card pressable padded={false}><ListRow …/></Card>
```

Nest at most one level. A card inside a card should drop to `lift={0}` and `tone="sunk"`.
