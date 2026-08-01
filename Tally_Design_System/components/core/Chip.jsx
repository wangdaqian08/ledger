import React from 'react';
import { Icon } from './Icon.jsx';

/** Tappable pill: filters, categories, split-mode switches. */
export function Chip({ children, icon, selected, tone = 'action', size = 'md', onClick, style, ...rest }) {
  const [down, setDown] = React.useState(false);
  const h = size === 'sm' ? 32 : 40;
  const accent = `var(--${tone})`;
  return (
    <button
      type="button"
      aria-pressed={!!selected}
      onClick={onClick}
      onPointerDown={() => setDown(true)}
      onPointerUp={() => setDown(false)}
      onPointerLeave={() => setDown(false)}
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 6,
        height: h, padding: size === 'sm' ? '0 12px' : '0 16px',
        background: selected ? accent : 'var(--surface-card)',
        color: selected ? 'var(--text-on-accent)' : 'var(--ink-2)',
        border: `2px solid ${selected ? 'var(--ink)' : 'var(--hairline-strong)'}`,
        borderRadius: 'var(--radius-chip)',
        fontFamily: 'var(--font-core)', fontSize: size === 'sm' ? 13 : 15,
        fontWeight: 'var(--weight-bold)', lineHeight: 1, whiteSpace: 'nowrap',
        boxShadow: selected ? `0 ${down ? 1 : 3}px 0 0 var(--ink)` : 'none',
        transform: `translateY(${selected && down ? 2 : 0}px) scale(${!selected && down ? 0.94 : 1})`,
        cursor: 'pointer',
        transition: 'all var(--dur-fast) var(--ease-spring)',
        ...style,
      }}
      {...rest}
    >
      {icon && <Icon name={icon} size={size === 'sm' ? 14 : 16} />}
      {children}
    </button>
  );
}
