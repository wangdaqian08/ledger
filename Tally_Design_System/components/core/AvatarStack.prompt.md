Who is in a group or an expense, compactly.

```jsx
<AvatarStack people={[{name:'Mei',hue:4},{name:'Sam',hue:2},{name:'Ade',hue:6}]} max={4} />
```

Use on group cards and expense rows. Beyond 4 faces the +N pip keeps the row from crowding the amount.

When the visible sliver of a stacked circle is under 22px (`size - overlap`), the overlapped avatars automatically drop to a single initial so the letter isn't sliced — at `size={28}`–`30` that is the normal case. Full names stay on the `title` tooltip.
