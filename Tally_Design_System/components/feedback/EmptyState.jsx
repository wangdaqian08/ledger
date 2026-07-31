import React from 'react';
import { Icon } from '../core/Icon.jsx';

/** Friendly nothing-here state. */
export function EmptyState({ icon = 'receipt', title, body, action, style, ...rest }) {
  return (
    <div style={{
      display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center',
      gap: 'var(--space-3)', padding: 'var(--space-10) var(--space-6)', ...style,
    }} {...rest}>
      <span style={{
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        width: 72, height: 72, background: 'var(--lemon)', color: 'var(--ink)',
        border: '2px solid var(--ink)', borderRadius: 'var(--radius-lg)',
        boxShadow: '0 4px 0 0 var(--ink)', transform: 'rotate(-4deg)',
      }}>
        <Icon name={icon} size={34} />
      </span>
      <h3 style={{
        margin: '6px 0 0', fontFamily: 'var(--font-core)', fontSize: 'var(--text-heading-lg)',
        fontWeight: 'var(--weight-black)', letterSpacing: 'var(--ls-heading-lg)', color: 'var(--ink)',
      }}>{title}</h3>
      {body && <p style={{ margin: 0, maxWidth: 280, fontSize: 'var(--text-body)', color: 'var(--text-muted)' }}>{body}</p>}
      {action && <div style={{ marginTop: 'var(--space-2)' }}>{action}</div>}
    </div>
  );
}
