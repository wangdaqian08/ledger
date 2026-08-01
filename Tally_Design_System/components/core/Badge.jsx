import React from 'react';

const T = {
  neutral: ['var(--bg-sunk)', 'var(--ink-2)'],
  action: ['var(--action-tint)', 'var(--action)'],
  mint: ['var(--mint-tint)', 'var(--mint-press)'],
  coral: ['var(--you-owe-tint)', 'var(--coral-press)'],
  lemon: ['var(--lemon-tint)', 'var(--ink)'],
  ink: ['var(--ink)', 'var(--text-on-accent)'],
};

/** Tiny uppercase status label. Not tappable — see Chip for that. */
export function Badge({ children, tone = 'neutral', style, ...rest }) {
  const [bg, fg] = T[tone] || T.neutral;
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', height: 22, padding: '0 9px',
      background: bg, color: fg,
      fontFamily: 'var(--font-core)', fontSize: 'var(--text-label)', fontWeight: 'var(--weight-black)',
      letterSpacing: 'var(--ls-label)', textTransform: 'uppercase', lineHeight: 1,
      borderRadius: 'var(--radius-pill)', whiteSpace: 'nowrap', ...style,
    }} {...rest}>{children}</span>
  );
}
