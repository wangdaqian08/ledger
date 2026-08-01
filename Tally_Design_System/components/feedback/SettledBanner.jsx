import React from 'react';
import { Icon } from '../core/Icon.jsx';

/** The celebration moment: everyone is square. */
export function SettledBanner({ message = "You're all square", sub, style, ...rest }) {
  return (
    <div style={{
      position: 'relative', overflow: 'hidden',
      display: 'flex', alignItems: 'center', gap: 'var(--space-3)',
      padding: 'var(--pad-card-lg)', background: 'var(--mint)', color: 'var(--text-on-accent)',
      border: '2px solid var(--ink)', borderRadius: 'var(--radius-card)',
      boxShadow: '0 4px 0 0 var(--ink)',
      animation: 'tally-settle-pop var(--dur-celebrate) var(--ease-spring)',
      ...style,
    }} {...rest}>
      <span style={{
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        width: 44, height: 44, flex: '0 0 auto', background: 'var(--paper)', color: 'var(--mint-press)',
        border: '2px solid var(--ink)', borderRadius: 'var(--radius-circle)',
      }}>
        <Icon name="party-popper" size={24} />
      </span>
      <div>
        <div style={{ fontFamily: 'var(--font-core)', fontSize: 'var(--text-heading-lg)', fontWeight: 'var(--weight-black)', letterSpacing: 'var(--ls-heading-lg)' }}>
          {message}
        </div>
        {sub && <div style={{ fontSize: 'var(--text-caption)', opacity: 0.9, marginTop: 2 }}>{sub}</div>}
      </div>
      <style>{'@keyframes tally-settle-pop{0%{transform:scale(0.9) rotate(-1deg);opacity:0}60%{transform:scale(1.03) rotate(0.4deg)}100%{transform:scale(1) rotate(0);opacity:1}}'}</style>
    </div>
  );
}
