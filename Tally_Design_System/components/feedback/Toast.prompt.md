Confirmation after a save, settle or delete. Springs up above the tab bar and leaves on its own after ~2.5s.

```jsx
<Toast open={saved} tone="mint" icon="check" message="Split saved" />
```

One toast at a time. Never use a toast for an error the user must act on — that's a Sheet.
