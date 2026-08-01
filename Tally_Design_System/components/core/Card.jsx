import React from 'react';

/** White slab with a hard ink border. The container for almost everything. */
export function Card({ children, tone = 'surface', lift = 2, pressable, padded = true, style, ...rest }) {
  const [down, setDown] = React.useState(false);
  const tones = {
    surface: { bg: 'var(--surface-card)', fg: 'var(--ink)' },
    sunk: { bg: 'var(--bg-sunk)', fg: 'var(--ink)' },
    action: { bg: 'var(--action-tint)', fg: 'var(--ink)' },
    mint: { bg: 'var(--mint-tint)', fg: 'var(--ink)' },
    coral: { bg: 'var(--you-owe-tint)', fg: 'var(--ink)' },
    lemon: { bg: 'var(--lemon-tint)', fg: 'var(--ink)' },
    ink: { bg: 'var(--ink)', fg: 'var(--text-on-accent)' },
  };
  const t = tones[tone] || tones.surface;
  const d = pressable && down;
  return (
    <div
      onPointerDown={pressable ? () => setDown(true) : undefined}
      onPointerUp={pressable ? () => setDown(false) : undefined}
      onPointerLeave={pressable ? () => setDown(false) : undefined}
      style={{
        background: t.bg,
        color: t.fg,
        border: '2px solid var(--ink)',
        borderRadius: 'var(--radius-card)',
        padding: padded ? 'var(--pad-card)' : 0,
        boxShadow: lift > 0 ? `0 ${d ? 1 : lift}px 0 0 var(--ink)` : 'none',
        transform: d ? `translateY(${lift - 1}px)` : 'none',
        cursor: pressable ? 'pointer' : undefined,
        transition: 'transform var(--dur-instant) var(--ease-out), box-shadow var(--dur-instant) var(--ease-out)',
        ...style,
      }}
      {...rest}
    >
      {children}
    </div>
  );
}
