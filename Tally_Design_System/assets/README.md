# assets/

## icons/

38 Lucide glyphs (`lucide-icons/lucide`, ISC licence) vendored as SVG — the exact set the product uses. `components/core/icon-paths.js` holds the same markup inlined so `Icon` can render a real `<svg>` with no network request; that is what makes icons appear correctly in screenshots, PDFs and PPTX exports. To add a glyph, drop the SVG here and add its inner markup to `icon-paths.js`.

## Everything else: empty on purpose

The provided sources (the `wangdaqian08/ledger` repository, the mounted `ledger/` folder) contained **no logo, no icons, no illustrations and no imagery** — only `README.md`, `LICENSE` and `.gitignore`. Nothing was drawn or generated to fill the gap.

Consequences, all documented in `readme.md`:

- **No logo.** The wordmark **Tally** is set in Figtree 900 wherever a mark would go.
- **Icons** are the vendored Lucide set above — a chosen substitute, since no icons were supplied.
- **Fonts** (Figtree, Space Grotesk) load from Google Fonts. Drop licensed binaries in `assets/fonts/` and swap the `@import` in `tokens/fonts.css` for real `@font-face` rules.
- **No photography or illustration** is part of the visual language — the product is flat warm paper.

Please add real brand assets here if they exist.
