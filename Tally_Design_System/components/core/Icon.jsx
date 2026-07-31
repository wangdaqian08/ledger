import React from 'react';
import { ICON_INNER } from './icon-paths.js';

/**
 * Tally uses Lucide (2px round-cap stroke) for every functional glyph.
 * Glyph markup is vendored in icon-paths.js and inlined as a real <svg>, so it
 * inherits \`currentColor\`, needs no network, and survives screenshot / PDF export.
 */
export function Icon({ name, size = 20, color = 'currentColor', strokeWidth = 2, style, ...rest }) {
  const markup = ICON_INNER[name];
  if (!markup) {
    // Unknown glyph: draw the "other" placeholder rather than a mystery block.
    return (
      <svg viewBox="0 0 24 24" width={size} height={size} aria-label={name} role="img"
        fill="none" stroke={color} strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round"
        style={{ display: 'block', flex: '0 0 auto', ...style }} {...rest}>
        <circle cx="12" cy="12" r="9" strokeDasharray="3 3" />
      </svg>
    );
  }
  return (
    <svg
      viewBox="0 0 24 24" width={size} height={size} role="img" aria-label={name}
      fill="none" stroke={color} strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round"
      style={{ display: 'block', flex: '0 0 auto', ...style }}
      dangerouslySetInnerHTML={{ __html: markup }}
      {...rest}
    />
  );
}
