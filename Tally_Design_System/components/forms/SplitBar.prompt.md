The signature Tally interaction — drag the handle between two people to move money between them. No typing.

```jsx
<SplitBar people={people} total={84.5} onChange={setPeople} />
```

Segments animate with the spring easing when set programmatically ("split evenly") and follow the finger 1:1 while dragging. Handles are 26px wide hit areas even though the visible pill is 14px. Above 5 people the bar gets cramped — fall back to a PersonRow list with Steppers.
