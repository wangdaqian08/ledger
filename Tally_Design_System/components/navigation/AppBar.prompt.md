Sticky screen header. Titles are 24px/800 and left-aligned — never centred iOS-style.

```jsx
<AppBar title="Osaka trip" subtitle="4 people" onBack={back}
        actions={<IconButton name="user-plus" label="Invite" />} />
```

Translucent blurred paper by default so content scrolls under it.
