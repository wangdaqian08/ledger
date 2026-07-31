Lucide glyph wrapper — use it for every functional icon in Tally instead of hand-writing SVG.

```jsx
<Icon name="receipt" size={20} />
<Icon name="plus" size={24} color="var(--text-on-accent)" />
```

The glyph is inlined as a real `<svg>` with `stroke="currentColor"`, so it takes the parent's text colour, needs no network, and survives screenshot / PDF / PPTX export. Names are Lucide slugs; the vendored set lives in `assets/icons/` and `components/core/icon-paths.js` (`ICON_NAMES` lists them). Adding a glyph means adding its SVG to both — an unknown name renders the dashed-circle placeholder.
