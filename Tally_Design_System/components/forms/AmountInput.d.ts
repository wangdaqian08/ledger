/**
 * The hero money readout on the Add Expense screen. Read-only display — pair with Keypad.
 * @startingPoint section="Forms" subtitle="Hero amount readout with keypad" viewport="700x340"
 */
export interface AmountInputProps {
  /** Already-formatted digit string, e.g. "48.20". */
  value?: string;
  currency?: string;
  label?: string;
  tone?: 'neutral' | 'owe' | 'owed';
  style?: React.CSSProperties;
}
export function AmountInput(props: AmountInputProps): JSX.Element;
