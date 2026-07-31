import React from 'react';

/** Slim slab progress meter — settle-up completion, budget used. */
export function ProgressBar({ value = 0, max = 100, tone = 'action', height = 14, label, style, ...rest }) {
  const p = Math.min(100, Math.max(0, (value / (max || 1)) * 100));
  return (
    <div style={{ ...style }} {...rest}>
      {label && (
        <div style={{
          display: 'flex', justifyContent: 'space-between', marginBottom: 6,
          fontSize: 'var(--text-caption)', fontWeight: 'var(--weight-bold)', color: 'var(--ink-2)',
        }}>
          <span>{label}</span><span>{Math.round(p)}%</span>
        </div>
      )}
      <div style={{
        height, background: 'var(--bg-sunk)', border: '2px solid var(--ink)',
        borderRadius: 'var(--radius-pill)', overflow: 'hidden',
      }}>
        <div style={{
          width: p + '%', height: '100%', background: `var(--${tone})`,
          borderRadius: 'var(--radius-pill)',
          transition: 'width var(--dur-slow) var(--ease-spring)',
        }} />
      </div>
    </div>
  );
}
