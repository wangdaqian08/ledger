import React from 'react';
import { Avatar } from '../core/Avatar.jsx';
import { Amount } from '../core/Amount.jsx';
import { Button } from '../core/Button.jsx';

/** "Who owes who" row with an inline settle action. */
export function BalanceRow({ name, hue = 1, amount = 0, onSettle, size = 'lg', divider = true, style }) {
  const owed = amount >= 0;
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 'var(--space-3)',
      padding: '12px var(--pad-card)', minHeight: 64,
      borderBottom: divider ? '1.5px solid var(--border-soft)' : 'none', ...style,
    }}>
      <Avatar name={name} hue={hue} size={40} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          fontSize: 'var(--text-heading-sm)', fontWeight: 'var(--weight-bold)', color: 'var(--ink)',
          lineHeight: 1.2, overflowWrap: 'anywhere',
        }}>{name}</div>
        <div style={{ fontSize: 'var(--text-caption)', color: 'var(--text-muted)', marginTop: 2 }}>
          {amount === 0 ? 'all square' : owed ? 'owes you' : 'you owe'}
        </div>
      </div>
      <Amount value={amount} tone={amount === 0 ? 'settled' : owed ? 'owed' : 'owe'} size={size} />
      {onSettle && amount !== 0 && (
        <Button size="sm" variant={owed ? 'soft' : 'solid'} tone={owed ? 'action' : 'mint'} onClick={onSettle}>
          {owed ? 'Remind' : 'Pay'}
        </Button>
      )}
    </div>
  );
}
