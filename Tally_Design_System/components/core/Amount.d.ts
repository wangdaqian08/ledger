/**
 * A money figure. Never render currency with the core sans — always through Amount.
 * @startingPoint section="Core" subtitle="Money type at every size and tone" viewport="700x220"
 */
export interface AmountProps {
  /** Numeric value. Sign drives the +/− glyph when showSign is on; color is set by tone. */
  value: number;
  /** Currency glyph prefix. */
  currency?: string;
  /** sm 14 · md 17 · lg 28 · hero 48 */
  size?: 'sm' | 'md' | 'lg' | 'hero';
  /** neutral = plain figure · owed = mint (they owe you) · owe = coral (you owe) · settled = grey · onDark = white */
  tone?: 'neutral' | 'owed' | 'owe' | 'settled' | 'onDark';
  /** Prefix an explicit + or − glyph. */
  showSign?: boolean;
  /** Transition the color when the tone flips. */
  animate?: boolean;
  style?: React.CSSProperties;
}
export function Amount(props: AmountProps): JSX.Element;
