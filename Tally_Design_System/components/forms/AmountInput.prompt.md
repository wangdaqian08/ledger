Hero amount readout — 48px Space Grotesk that bumps on every keystroke.

```jsx
<AmountInput label="Total" value={digits} />
<Keypad onKey={handleKey} />
```

Never wire this to a native `<input type="number">` on mobile; the OS keyboard is the wrong affordance. Always drive it with Keypad.
