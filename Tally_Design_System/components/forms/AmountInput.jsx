import React from 'react';

/** Big money display driven by Keypad, not by a system keyboard. */
export function AmountInput({ value = '0', currency = '$', label, tone = 'neutral', style, ...rest }) {
  const color = tone === 'owe' ? 'var(--you-owe)' : tone === 'owed' ? 'var(--owed-to-you)' : 'var(--ink)';
  const [bump, setBump] = React.useState(0);
  React.useEffect(() => { setBump((b) => b + 1); }, [value]);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6, ...style }} {...rest}>
      {label && (
        <span style={{
          fontFamily: 'var(--font-core)', fontSize: 'var(--text-label)', fontWeight: 'var(--weight-black)',
          letterSpacing: 'var(--ls-label)', textTransform: 'uppercase', color: 'var(--text-muted)',
        }}>{label}</span>
      )}
      <div
        key={bump}
        style={{
          display: 'flex', alignItems: 'baseline', gap: 2,
          fontFamily: 'var(--font-money)', fontVariantNumeric: 'tabular-nums',
          fontWeight: 'var(--weight-bold)', color, lineHeight: 1,
          animation: 'tally-amount-bump var(--dur-fast) var(--ease-spring)',
        }}
      >
        <span style={{ fontSize: 'calc(var(--text-money-hero) * 0.56)', color: 'var(--text-muted)' }}>{currency}</span>
        <span style={{ fontSize: 'var(--text-money-hero)', letterSpacing: '-0.03em' }}>{value}</span>
      </div>
      <style>{'@keyframes tally-amount-bump{from{transform:scale(0.94)}to{transform:scale(1)}}'}</style>
    </div>
  );
}
