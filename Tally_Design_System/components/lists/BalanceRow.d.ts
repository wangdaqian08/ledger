/**
 * Per-person balance with an inline Remind / Pay action.
 * @startingPoint section="Lists" subtitle="Who owes who, with settle actions" viewport="700x300"
 */
export interface BalanceRowProps {
  name: string;
  /** Person hue 1-8. */
  hue?: number;
  /** Positive = they owe you. Negative = you owe them. 0 = all square. */
  amount?: number;
  /** Amount type size. Drop to 'md' when the row also carries a caption or a wider action. */
  size?: 'sm' | 'md' | 'lg';
  /** Omit to render a read-only balance. */
  onSettle?: () => void;
  divider?: boolean;
  style?: React.CSSProperties;
}
export function BalanceRow(props: BalanceRowProps): JSX.Element;
