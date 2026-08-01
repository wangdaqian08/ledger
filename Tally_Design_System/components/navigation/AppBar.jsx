import React from 'react';
import { IconButton } from '../core/IconButton.jsx';

/** Top bar. Title left-aligned and heavy; actions right. */
export function AppBar({ title, subtitle, onBack, actions, tone = 'paper', style, ...rest }) {
  const dark = tone === 'ink';
  return (
    <header
      style={{
        position: 'sticky', top: 0, zIndex: 20,
        display: 'flex', alignItems: 'center', gap: 'var(--space-2)',
        minHeight: 'var(--appbar-h)', padding: '8px var(--gutter-screen)',
        background: dark ? 'var(--ink)' : 'color-mix(in srgb, var(--paper) 88%, transparent)',
        backdropFilter: dark ? undefined : 'saturate(180%) blur(14px)',
        WebkitBackdropFilter: dark ? undefined : 'saturate(180%) blur(14px)',
        borderBottom: `2px solid ${dark ? 'var(--ink)' : 'var(--border-soft)'}`,
        color: dark ? 'var(--text-on-accent)' : 'var(--ink)',
        ...style,
      }}
      {...rest}
    >
      {onBack && <IconButton name="arrow-left" size={44} label="Back" onClick={onBack} style={{ marginLeft: -10, color: 'inherit' }} />}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          fontFamily: 'var(--font-core)', fontSize: 'var(--text-title)', fontWeight: 'var(--weight-black)',
          letterSpacing: 'var(--ls-title)', lineHeight: 1.1,
          overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
        }}>{title}</div>
        {subtitle && (
          <div style={{ fontSize: 'var(--text-caption)', color: dark ? 'var(--ink-4)' : 'var(--text-muted)', marginTop: 1 }}>
            {subtitle}
          </div>
        )}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-1)' }}>{actions}</div>
    </header>
  );
}
