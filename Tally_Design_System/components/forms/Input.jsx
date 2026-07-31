import React from 'react';
import { Icon } from '../core/Icon.jsx';

/** Text field. Tally uses these sparingly — prefer chips, steppers and drags. */
export function Input({ label, icon, hint, error, value, onChange, placeholder, type = 'text', style, ...rest }) {
  const [focus, setFocus] = React.useState(false);
  const border = error ? 'var(--coral)' : focus ? 'var(--action)' : 'var(--hairline-strong)';
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 6, ...style }}>
      {label && (
        <span style={{
          fontFamily: 'var(--font-core)', fontSize: 'var(--text-label)', fontWeight: 'var(--weight-black)',
          letterSpacing: 'var(--ls-label)', textTransform: 'uppercase', color: 'var(--text-muted)',
        }}>{label}</span>
      )}
      <span style={{
        display: 'flex', alignItems: 'center', gap: 10,
        minHeight: 52, padding: '0 14px',
        background: 'var(--surface-card)',
        border: `2px solid ${border}`,
        borderRadius: 'var(--radius-input)',
        boxShadow: focus ? '0 3px 0 0 var(--ink)' : 'none',
        transition: 'border-color var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out)',
      }}>
        {icon && <Icon name={icon} size={18} color="var(--ink-3)" />}
        <input
          type={type}
          value={value}
          placeholder={placeholder}
          onChange={onChange}
          onFocus={() => setFocus(true)}
          onBlur={() => setFocus(false)}
          style={{
            flex: 1, minWidth: 0, border: 'none', outline: 'none', background: 'transparent',
            fontFamily: 'var(--font-core)', fontSize: 'var(--text-body-lg)', fontWeight: 'var(--weight-medium)',
            color: 'var(--ink)', padding: 0,
          }}
          {...rest}
        />
      </span>
      {(hint || error) && (
        <span style={{ fontSize: 'var(--text-caption)', color: error ? 'var(--coral)' : 'var(--text-muted)' }}>
          {error || hint}
        </span>
      )}
    </label>
  );
}
