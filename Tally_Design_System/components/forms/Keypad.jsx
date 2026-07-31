import React from 'react';
import { Icon } from '../core/Icon.jsx';

const KEYS = ['1','2','3','4','5','6','7','8','9','.','0','del'];

/** Custom numeric pad — Tally never opens the OS keyboard for money. */
export function Keypad({ onKey, style, ...rest }) {
  const [down, setDown] = React.useState(null);
  return (
    <div
      style={{
        display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 'var(--space-3)',
        width: '100%', ...style,
      }}
      {...rest}
    >
      {KEYS.map((k) => (
        <button
          key={k}
          type="button"
          aria-label={k === 'del' ? 'Delete' : k}
          onClick={() => onKey && onKey(k)}
          onPointerDown={() => setDown(k)}
          onPointerUp={() => setDown(null)}
          onPointerLeave={() => setDown(null)}
          style={{
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            minHeight: 56,
            background: k === 'del' ? 'var(--bg-sunk)' : 'var(--surface-card)',
            color: 'var(--ink)',
            border: '2px solid var(--ink)',
            borderRadius: 'var(--radius-md)',
            fontFamily: 'var(--font-money)', fontSize: 24, fontWeight: 'var(--weight-bold)',
            boxShadow: `0 ${down === k ? 1 : 3}px 0 0 var(--ink)`,
            transform: down === k ? 'translateY(2px)' : 'none',
            cursor: 'pointer',
            transition: 'transform var(--dur-instant) var(--ease-out), box-shadow var(--dur-instant) var(--ease-out)',
          }}
        >
          {k === 'del' ? <Icon name="delete" size={22} /> : k}
        </button>
      ))}
    </div>
  );
}
