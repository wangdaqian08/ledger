The base row for every list in Tally. Put rows inside `<Card padded={false}>`.

```jsx
<Card padded={false}>
  <ListRow leading={<Avatar name="Mei" hue={4} size={36} />} title="Mei" subtitle="paid the taxi"
           trailing={<Amount value={12.5} tone="owed" />} />
  <ListRow title="Sam" divider={false} chevron />
</Card>
```

Set `divider={false}` on the final row so the card's border isn't doubled.
