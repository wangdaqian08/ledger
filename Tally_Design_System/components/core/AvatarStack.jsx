import React from 'react';
import { Avatar } from './Avatar.jsx';

/** Overlapping row of members with a +N overflow pip. */
export function AvatarStack({ people = [], size = 32, max = 4, overlap = 10, style, ...rest }) {
  const shown = people.slice(0, max);
  const rest_ = people.length - shown.length;
  // Only `size - overlap` px of each stacked circle is visible; two initials need ~22px.
  const compact = size - overlap < 22;
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', ...style }} {...rest}>
      {shown.map((p, i) => (
        <span key={p.name + i} style={{ marginLeft: i === 0 ? 0 : -overlap, zIndex: shown.length - i }}>
          <Avatar name={p.name} hue={p.hue} size={size} compact={compact && i > 0} />
        </span>
      ))}
      {rest_ > 0 && (
        <span style={{
          marginLeft: -overlap, display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          width: size, height: size, borderRadius: 'var(--radius-circle)',
          background: 'var(--bg-sunk)', color: 'var(--ink-2)', border: '2px solid var(--ink)',
          fontFamily: 'var(--font-core)', fontSize: Math.round(size * 0.34), fontWeight: 'var(--weight-black)',
        }}>+{rest_}</span>
      )}
    </span>
  );
}
