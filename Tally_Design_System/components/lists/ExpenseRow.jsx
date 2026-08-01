import React from 'react';
import { Icon } from '../core/Icon.jsx';
import { Amount } from '../core/Amount.jsx';
import { ListRow } from './ListRow.jsx';

const CAT_HUE = { food: 2, drinks: 3, transport: 5, stay: 7, groceries: 4, fun: 8, home: 6, other: 1 };
const CAT_ICON = { food: 'utensils', drinks: 'beer', transport: 'car-front', stay: 'bed-double', groceries: 'shopping-basket', fun: 'ticket', home: 'house', other: 'circle-dashed' };

/** One expense in a group feed: category disc, title, who paid, your share. */
export function ExpenseRow({ title, category = 'other', paidBy, date, yourShare = 0, settled, onClick, divider = true, style }) {
  const hue = CAT_HUE[category] || 1;
  return (
    <ListRow
      onClick={onClick}
      divider={divider}
      style={style}
      leading={
        <span style={{
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          width: 44, height: 44, flex: '0 0 auto',
          background: `var(--person-${hue})`, border: '2px solid var(--ink)',
          borderRadius: 'var(--radius-md)', color: hue === 3 ? 'var(--ink)' : 'var(--text-on-accent)',
        }}>
          <Icon name={CAT_ICON[category] || 'circle-dashed'} size={22} />
        </span>
      }
      title={title}
      subtitle={[paidBy && `${paidBy} paid`, date].filter(Boolean).join(' · ')}
      trailing={
        <span style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 2 }}>
          <Amount value={yourShare} tone={settled ? 'settled' : yourShare < 0 ? 'owe' : 'owed'} showSign={!settled} />
          <span style={{ fontSize: 11, fontWeight: 'var(--weight-bold)', letterSpacing: '0.05em', textTransform: 'uppercase', color: 'var(--text-subtle)' }}>
            {settled ? 'settled' : yourShare < 0 ? 'you owe' : 'you get'}
          </span>
        </span>
      }
    />
  );
}
