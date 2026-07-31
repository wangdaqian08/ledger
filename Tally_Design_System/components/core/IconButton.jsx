import React from 'react';
import { Icon } from './Icon.jsx';

/** Circular tap target for app bars, dismiss controls and row affordances. */
export function IconButton({ name, size = 48, tone = 'ink', variant = 'plain', label, style, ...rest }) {
  const [down, setDown] = React.useState(false);
  const fills = {
    plain: { bg: 'transparent', fg: 'var(--ink)', border: 'none', edge: false },
    surface: { bg: 'var(--surface-card)', fg: 'var(--ink)', border: '2px solid var(--ink)', edge: true },
    tint: { bg: 'var(--action-tint)', fg: 'var(--action)', border: 'none', edge: false },
    solid: { bg: `var(--${tone === 'ink' ? 'ink' : tone})`, fg: 'var(--text-on-accent)', border: '2px solid var(--ink)', edge: true },
  };
  const k = fills[variant] || fills.plain;
  return (
    <button
      type="button"
      aria-label={label || name}
      onPointerDown={() => setDown(true)}
      onPointerUp={() => setDown(false)}
      onPointerLeave={() => setDown(false)}
      style={{
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        width: size, height: size, flex: '0 0 auto', padding: 0,
        borderRadius: 'var(--radius-circle)',
        background: k.bg, color: k.fg, border: k.border,
        boxShadow: k.edge ? `0 ${down ? 1 : 3}px 0 0 var(--ink)` : 'none',
        transform: k.edge && down ? 'translateY(2px)' : `scale(${down ? 0.9 : 1})`,
        cursor: 'pointer',
        transition: 'transform var(--dur-instant) var(--ease-out), box-shadow var(--dur-instant) var(--ease-out)',
        ...style,
      }}
      {...rest}
    >
      <Icon name={name} size={Math.round(size * 0.44)} />
    </button>
  );
}
