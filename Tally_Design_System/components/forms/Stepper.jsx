import React from 'react';
import { IconButton } from '../core/IconButton.jsx';

/** +/− control for share counts. Tap, never type. */
export function Stepper({ value = 1, min = 0, max = 99, onChange, suffix, style, ...rest }) {
  const set = (v) => onChange && onChange(Math.min(max, Math.max(min, v)));
  return (
    <div style={{ display: 'inline-flex', alignItems: 'center', gap: 'var(--space-3)', ...style }} {...rest}>
      <IconButton name="minus" size={40} variant="surface" label="Decrease" onClick={() => set(value - 1)} />
      <span style={{
        minWidth: 44, textAlign: 'center',
        fontFamily: 'var(--font-money)', fontVariantNumeric: 'tabular-nums',
        fontSize: 'var(--text-money-lg)', fontWeight: 'var(--weight-bold)', color: 'var(--ink)', lineHeight: 1,
      }}>{value}{suffix}</span>
      <IconButton name="plus" size={40} variant="surface" label="Increase" onClick={() => set(value + 1)} />
    </div>
  );
}
