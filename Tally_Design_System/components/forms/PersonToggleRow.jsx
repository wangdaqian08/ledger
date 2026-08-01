import React from 'react';
import { Avatar } from '../core/Avatar.jsx';

/** Tap avatars to include/exclude people from a split. */
export function PersonToggleRow({ people = [], selected = [], onToggle, size = 52, style, ...rest }) {
  return (
    <div style={{ display: 'flex', gap: 'var(--space-3)', overflowX: 'auto', paddingBottom: 4, ...style }} {...rest}>
      {people.map((p) => {
        const on = selected.includes(p.name);
        return (
          <button
            key={p.name}
            type="button"
            aria-pressed={on}
            onClick={() => onToggle && onToggle(p.name)}
            style={{
              display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6,
              border: 'none', background: 'transparent', padding: '2px 0', cursor: 'pointer', flex: '0 0 auto',
              transform: on ? 'translateY(-2px)' : 'none',
              transition: 'transform var(--dur-fast) var(--ease-spring)',
            }}
          >
            <Avatar name={p.name} hue={p.hue} size={size} selected={on} dimmed={!on} />
            <span style={{
              fontFamily: 'var(--font-core)', fontSize: 'var(--text-caption)',
              fontWeight: on ? 'var(--weight-black)' : 'var(--weight-medium)',
              color: on ? 'var(--ink)' : 'var(--text-subtle)', maxWidth: size + 16,
              overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
            }}>{p.name.split(' ')[0]}</span>
          </button>
        );
      })}
    </div>
  );
}
