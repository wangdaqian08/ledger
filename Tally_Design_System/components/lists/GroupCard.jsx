import React from 'react';
import { Card } from '../core/Card.jsx';
import { Icon } from '../core/Icon.jsx';
import { Amount } from '../core/Amount.jsx';
import { AvatarStack } from '../core/AvatarStack.jsx';
import { Badge } from '../core/Badge.jsx';

/** Home-screen tile for one group. */
export function GroupCard({ name, icon = 'users', hue = 6, members = [], balance = 0, onClick, style }) {
  const settled = Math.abs(balance) < 0.005;
  return (
    <Card pressable lift={4} onClick={onClick} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', ...style }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
        <span style={{
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          width: 44, height: 44, flex: '0 0 auto', background: `var(--person-${hue})`,
          border: '2px solid var(--ink)', borderRadius: 'var(--radius-md)',
          color: hue === 3 ? 'var(--ink)' : 'var(--text-on-accent)',
        }}>
          <Icon name={icon} size={22} />
        </span>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{
            fontSize: 'var(--text-heading-lg)', fontWeight: 'var(--weight-black)',
            letterSpacing: 'var(--ls-heading-lg)', color: 'var(--ink)',
            overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
          }}>{name}</div>
          <div style={{ fontSize: 'var(--text-caption)', color: 'var(--text-muted)', marginTop: 2 }}>
            {members.length} {members.length === 1 ? 'person' : 'people'}
          </div>
        </div>
        <Icon name="chevron-right" size={20} color="var(--ink-4)" />
      </div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-3)' }}>
        <AvatarStack people={members} size={30} max={5} />
        {settled
          ? <Badge tone="mint">All square</Badge>
          : <span style={{ display: 'flex', alignItems: 'baseline', gap: 6 }}>
              <span style={{ fontSize: 11, fontWeight: 'var(--weight-black)', letterSpacing: '0.05em', textTransform: 'uppercase', color: 'var(--text-subtle)' }}>
                {balance > 0 ? 'you get' : 'you owe'}
              </span>
              <Amount value={balance} tone={balance > 0 ? 'owed' : 'owe'} size="lg" />
            </span>}
      </div>
    </Card>
  );
}
