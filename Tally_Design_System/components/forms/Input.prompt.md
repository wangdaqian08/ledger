Text field — use only when free text is genuinely required (expense title, group name, invite email).

```jsx
<Input label="What was it?" icon="receipt" placeholder="Dinner at Sichuan Rose" />
<Input label="Email" type="email" error="That address looks off" />
```

Tally is a low-input product: if the answer is one of a known set, use Chip; if it's a number of people or shares, use Stepper; if it's a proportion, use SplitBar.
