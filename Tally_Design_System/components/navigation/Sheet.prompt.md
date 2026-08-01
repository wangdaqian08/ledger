Every create, edit and confirm flow in Tally is a bottom sheet — Tally has almost no full-page forms.

```jsx
<Sheet open={open} onClose={close} title="Add expense"
       footer={<Button block size="lg">Save</Button>}>
  …
</Sheet>
```

Springs in over `--dur-slow`, eases straight out. The parent needs `position: relative` since the sheet is absolutely positioned to the screen frame.
