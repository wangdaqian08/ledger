/**
 * A single expense in a group feed.
 * @startingPoint section="Lists" subtitle="Expense feed rows by category" viewport="700x300"
 */
export interface ExpenseRowProps {
  title: string;
  /** One of the eight Tally categories — drives the disc colour and glyph. */
  category?: 'food' | 'drinks' | 'transport' | 'stay' | 'groceries' | 'fun' | 'home' | 'other';
  /** First name of whoever fronted the money. */
  paidBy?: string;
  /** Relative or short date string, e.g. "Yesterday". */
  date?: string;
  /** Your position on this expense. Positive = owed to you, negative = you owe. */
  yourShare?: number;
  /** Renders grey with a "settled" caption. */
  settled?: boolean;
  divider?: boolean;
  onClick?: (e: React.MouseEvent) => void;
  style?: React.CSSProperties;
}
export function ExpenseRow(props: ExpenseRowProps): JSX.Element;
