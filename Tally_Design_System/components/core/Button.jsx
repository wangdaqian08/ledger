import React from 'react';
import { Icon } from './Icon.jsx';

const TONES = {
  action: { bg: 'var(--action)', fg: 'var(--text-on-accent)', edge: 'var(--action-press)' },
  mint:   { bg: 'var(--mint)',   fg: 'var(--text-on-accent)', edge: 'var(--mint-press)' },
  coral:  { bg: 'var(--coral)',  fg: 'var(--text-on-accent)', edge: 'var(--coral-press)' },
  lemon:  { bg: 'var(--lemon)',  fg: 'var(--text-on-lemon)',  edge: 'var(--lemon-press)' },
  ink:    { bg: 'var(--ink)',    fg: 'var(--text-on-accent)', edge: '#000000' },
};

const SIZES = {
  sm: { h: 40, px: 16, fs: 14, gap: 6, icon: 16 },
  md: { h: 48, px: 22, fs: 16, gap: 8, icon: 18 },
  lg: { h: 56, px: 28, fs: 18, gap: 10, icon: 20 },
};

/**
 * The pressable slab. Primary action grammar across all of Tally.
 */
export function Button({
  children, variant = 'primary', tone = 'action', size = 'md',
  icon, iconRight, block, disabled, style, ...rest
}) {
  const [down, setDown] = React.useState(false);
  const t = TONES[tone] || TONES.action;
  const s = SIZES[size] || SIZES.md;

  const skins = {
    primary: { background: t.bg, color: t.fg, border: '2px solid var(--ink)', edge: 'var(--ink)' },
    solid:   { background: t.bg, color: t.fg, border: '2px solid transparent', edge: t.edge },
    outline: { background: 'var(--surface-card)', color: 'var(--ink)', border: '2px solid var(--ink)', edge: 'var(--ink)' },
    soft:    { background: 'var(--action-tint)', color: 'var(--action)', border: '2px solid transparent', edge: 'transparent' },
    ghost:   { background: 'transparent', color: 'var(--action)', border: '2px solid transparent', edge: 'transparent' },
  };
  const k = skins[variant] || skins.primary;
  const lifted = k.edge !== 'transparent';

  return (
    <button
      type="button"
      disabled={disabled}
      onPointerDown={() => setDown(true)}
      onPointerUp={() => setDown(false)}
      onPointerLeave={() => setDown(false)}
      style={{
        display: block ? 'flex' : 'inline-flex',
        width: block ? '100%' : undefined,
        alignItems: 'center',
        justifyContent: 'center',
        gap: s.gap,
        minHeight: s.h,
        padding: `0 ${s.px}px`,
        fontFamily: 'var(--font-core)',
        fontSize: s.fs,
        fontWeight: 'var(--weight-bold)',
        letterSpacing: '-0.01em',
        lineHeight: 1,
        borderRadius: 'var(--radius-button)',
        background: k.background,
        color: k.color,
        border: k.border,
        boxShadow: lifted ? `0 ${down ? 1 : 4}px 0 0 ${k.edge}` : 'none',
        transform: `translateY(${lifted && down ? 3 : 0}px)`,
        opacity: disabled ? 0.42 : 1,
        cursor: disabled ? 'not-allowed' : 'pointer',
        transition: 'transform var(--dur-instant) var(--ease-out), box-shadow var(--dur-instant) var(--ease-out), background var(--dur-fast) var(--ease-out)',
        ...style,
      }}
      {...rest}
    >
      {icon && <Icon name={icon} size={s.icon} />}
      {children}
      {iconRight && <Icon name={iconRight} size={s.icon} />}
    </button>
  );
}
