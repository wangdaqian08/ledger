import React from 'react';
import { Icon } from '../core/Icon.jsx';

/** Bottom tab bar with a centre action slab. */
export function TabBar({ tabs = [], value, onChange, centerAction, style, ...rest }) {
  const half = Math.ceil(tabs.length / 2);
  const groups = centerAction ? [tabs.slice(0, half), tabs.slice(half)] : [tabs];
  const [down, setDown] = React.useState(false);

  const Tab = (t) => {
    const on = t.id === value;
    return (
      <button key={t.id} type="button" onClick={() => onChange && onChange(t.id)}
        style={{
          flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 3,
          minHeight: 'var(--tabbar-h)', border: 'none', background: 'transparent', cursor: 'pointer',
          color: on ? 'var(--action)' : 'var(--ink-4)',
          transition: 'color var(--dur-fast) var(--ease-out)',
        }}>
        <span style={{ transform: on ? 'translateY(-1px) scale(1.08)' : 'none', transition: 'transform var(--dur-fast) var(--ease-spring)' }}>
          <Icon name={t.icon} size={24} />
        </span>
        <span style={{ fontFamily: 'var(--font-core)', fontSize: 11, fontWeight: on ? 'var(--weight-black)' : 'var(--weight-semibold)', letterSpacing: '0.01em' }}>
          {t.label}
        </span>
      </button>
    );
  };

  return (
    <nav
      style={{
        position: 'sticky', bottom: 0, zIndex: 20,
        display: 'flex', alignItems: 'center',
        background: 'var(--surface-card)',
        borderTop: '2px solid var(--ink)',
        paddingBottom: 'var(--safe-bottom)',
        ...style,
      }}
      {...rest}
    >
      {groups[0].map(Tab)}
      {centerAction && (
        <button type="button" onClick={centerAction.onClick} aria-label={centerAction.label}
          onPointerDown={() => setDown(true)} onPointerUp={() => setDown(false)} onPointerLeave={() => setDown(false)}
          style={{
            flex: '0 0 auto', width: 60, height: 60, marginTop: -22,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            background: 'var(--action)', color: 'var(--text-on-accent)',
            border: '2.5px solid var(--ink)', borderRadius: 'var(--radius-circle)',
            boxShadow: `0 ${down ? 1 : 4}px 0 0 var(--ink)`,
            transform: down ? 'translateY(3px)' : 'none', cursor: 'pointer',
            transition: 'transform var(--dur-instant) var(--ease-out), box-shadow var(--dur-instant) var(--ease-out)',
          }}>
          <Icon name={centerAction.icon || 'plus'} size={30} />
        </button>
      )}
      {groups[1] && groups[1].map(Tab)}
    </nav>
  );
}
