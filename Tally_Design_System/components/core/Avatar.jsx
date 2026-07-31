import React from 'react';

const PERSON_HUES = 8;

function initials(name = '', single = false) {
  const parts = String(name).trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return '?';
  if (single) return parts[0][0].toUpperCase();
  return (parts[0][0] + (parts[1] ? parts[1][0] : '')).toUpperCase();
}

/** Person marker. Each member of a group gets a fixed hue from --person-1..8. */
export function Avatar({ name, hue = 1, size = 40, selected, dimmed, badge, compact, style, ...rest }) {
  const h = ((Number(hue) - 1) % PERSON_HUES + PERSON_HUES) % PERSON_HUES + 1;
  return (
    <span style={{ position: 'relative', display: 'inline-flex', flex: '0 0 auto' }}>
      <span
        title={name}
        style={{
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          width: size, height: size, borderRadius: 'var(--radius-circle)',
          background: `var(--person-${h})`,
          color: h === 3 ? 'var(--ink)' : 'var(--text-on-accent)',
          border: '2px solid var(--ink)',
          fontFamily: 'var(--font-core)', fontWeight: 'var(--weight-black)',
          fontSize: Math.max(11, Math.round(size * (compact ? 0.44 : 0.38))), lineHeight: 1,
          letterSpacing: '0.01em',
          boxShadow: selected ? '0 0 0 3px var(--paper), 0 0 0 6px var(--ink)' : 'none',
          opacity: dimmed ? 0.32 : 1,
          filter: dimmed ? 'saturate(0.4)' : 'none',
          transition: 'opacity var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-spring), filter var(--dur-fast) var(--ease-out)',
          ...style,
        }}
        {...rest}
      >
        {initials(name, compact)}
      </span>
      {badge != null && (
        <span style={{
          position: 'absolute', right: -4, bottom: -4, minWidth: 18, height: 18, padding: '0 4px',
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          background: 'var(--ink)', color: 'var(--text-on-accent)', border: '2px solid var(--paper)',
          borderRadius: 'var(--radius-pill)', fontFamily: 'var(--font-money)', fontSize: 10,
          fontWeight: 'var(--weight-bold)', lineHeight: 1,
        }}>{badge}</span>
      )}
    </span>
  );
}
