Tally's own numeric pad. Big slab keys that sink on press; replaces the iOS keyboard for all money entry.

```jsx
<Keypad onKey={(k) => setDigits(applyKey(digits, k))} />
```

Always full width, pinned to the bottom of the entry sheet above the CTA.
