import React from 'react';
import { Chip } from '../core/Chip.jsx';

export const CATEGORIES = [
  { id: 'food', label: 'Food', icon: 'utensils' },
  { id: 'drinks', label: 'Drinks', icon: 'beer' },
  { id: 'transport', label: 'Transport', icon: 'car-front' },
  { id: 'stay', label: 'Stay', icon: 'bed-double' },
  { id: 'groceries', label: 'Groceries', icon: 'shopping-basket' },
  { id: 'fun', label: 'Fun', icon: 'ticket' },
  { id: 'home', label: 'Home', icon: 'house' },
  { id: 'other', label: 'Other', icon: 'circle-dashed' },
];

/** Scrolling chip row of expense categories. */
export function CategoryPicker({ value, onChange, categories = CATEGORIES, style, ...rest }) {
  return (
    <div style={{
      display: 'flex', gap: 'var(--gap-inline)', overflowX: 'auto',
      paddingBottom: 6, WebkitOverflowScrolling: 'touch', ...style,
    }} {...rest}>
      {categories.map((c) => (
        <Chip key={c.id} icon={c.icon} selected={value === c.id} onClick={() => onChange && onChange(c.id)}>
          {c.label}
        </Chip>
      ))}
    </div>
  );
}
