import React from 'react';
import { Icon } from '../core/Icon.jsx';

/** Generic row: leading slot, title + subtitle, trailing slot, optional chevron. */
export function ListRow({ leading, title, subtitle, trailing, chevron, onClick, divider = true, style, ...rest }) {
  const [down, setDown] = React.useState(false);
  return (
    <div
      onClick={onClick}
      onPointerDown={onClick ? () => setDown(true) : undefined}
      onPointerUp={onClick ? () => setDown(false) : undefined}
      onPointerLeave={onClick ? () => setDown(false) : undefined}
      style={{
        display: 'flex', alignItems: 'center', gap: 'var(--space-3)',
        minHeight: 'var(--tap-min)', padding: '12px var(--pad-card)',
        borderBottom: divider ? '1.5px solid var(--border-soft)' : 'none',
        background: down ? 'var(--surface-card-hover)' : 'transparent',
        cursor: onClick ? 'pointer' : undefined,
        transition: 'background var(--dur-fast) var(--ease-out)',
        ...style,
      }}
      {...rest}
    >
      {leading}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          fontFamily: 'var(--font-core)', fontSize: 'var(--text-heading-sm)',
          fontWeight: 'var(--weight-bold)', color: 'var(--ink)', letterSpacing: 'var(--ls-heading-sm)',
          overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
        }}>{title}</div>
        {subtitle && (
          <div style={{
            fontSize: 'var(--text-caption)', color: 'var(--text-muted)', marginTop: 2,
            overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
          }}>{subtitle}</div>
        )}
      </div>
      {trailing}
      {chevron && <Icon name="chevron-right" size={18} color="var(--ink-4)" />}
    </div>
  );
}
