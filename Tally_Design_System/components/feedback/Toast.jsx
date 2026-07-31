import React from 'react';
import { Icon } from '../core/Icon.jsx';

/** Transient ink capsule that drops in from the bottom. */
export function Toast({ open, message, icon = 'check', tone = 'ink', action, style, ...rest }) {
  const bg = tone === 'mint' ? 'var(--mint)' : tone === 'coral' ? 'var(--coral)' : 'var(--ink)';
  return (
    <div
      role="status"
      style={{
        position: 'absolute', left: 'var(--gutter-screen)', right: 'var(--gutter-screen)',
        bottom: `calc(var(--tabbar-h) + var(--space-4))`, zIndex: 80,
        display: 'flex', alignItems: 'center', gap: 'var(--space-3)',
        padding: '12px 16px', background: bg, color: 'var(--text-on-accent)',
        border: '2px solid var(--ink)', borderRadius: 'var(--radius-pill)',
        boxShadow: 'var(--lift-toast)',
        fontFamily: 'var(--font-core)', fontSize: 'var(--text-body)', fontWeight: 'var(--weight-bold)',
        opacity: open ? 1 : 0,
        transform: open ? 'translateY(0) scale(1)' : 'translateY(20px) scale(0.96)',
        pointerEvents: open ? 'auto' : 'none',
        transition: 'opacity var(--dur-base) var(--ease-out), transform var(--dur-base) var(--ease-spring)',
        ...style,
      }}
      {...rest}
    >
      <Icon name={icon} size={18} />
      <span style={{ flex: 1, minWidth: 0 }}>{message}</span>
      {action}
    </div>
  );
}
