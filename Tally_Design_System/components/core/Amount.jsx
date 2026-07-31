import React from 'react';

const SIZES = { sm: 'var(--text-money-sm)', md: 'var(--text-money)', lg: 'var(--text-money-lg)', hero: 'var(--text-money-hero)' };
const TONES = { neutral: 'var(--ink)', owed: 'var(--owed-to-you)', owe: 'var(--you-owe)', settled: 'var(--settled)', onDark: 'var(--text-on-accent)' };

/** Money. Always Space Grotesk, always tabular, sign carried by color. */
export function Amount({ value, currency = '$', size = 'md', tone = 'neutral', showSign = false, animate = false, style, ...rest }) {
  const n = Math.abs(Number(value) || 0);
  const sign = showSign ? (Number(value) < 0 ? '−' : '+') : '';
  const body = n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return (
    <span
      style={{
        fontFamily: 'var(--font-money)',
        fontVariantNumeric: 'tabular-nums',
        fontFeatureSettings: '"tnum" 1',
        fontSize: SIZES[size] || SIZES.md,
        fontWeight: size === 'hero' || size === 'lg' ? 'var(--weight-bold)' : 'var(--weight-medium)',
        letterSpacing: size === 'hero' ? '-0.03em' : '-0.01em',
        lineHeight: 1,
        color: TONES[tone] || TONES.neutral,
        whiteSpace: 'nowrap',
        transition: animate ? 'color var(--dur-base) var(--ease-out)' : undefined,
        ...style,
      }}
      {...rest}
    >
      {sign}{currency}{body}
    </span>
  );
}
