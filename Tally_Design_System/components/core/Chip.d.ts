/**
 * Tappable pill for filters, categories and mode switches.
 * @startingPoint section="Core" subtitle="Selectable chips and category row" viewport="700x180"
 */
export interface ChipProps {
  children?: React.ReactNode;
  /** Lucide slug shown before the label. */
  icon?: string;
  /** Selected chips fill with the tone color and gain an ink edge. */
  selected?: boolean;
  tone?: 'action' | 'mint' | 'coral' | 'lemon';
  /** sm 32px · md 40px (default) */
  size?: 'sm' | 'md';
  onClick?: (e: React.MouseEvent) => void;
  style?: React.CSSProperties;
}
export function Chip(props: ChipProps): JSX.Element;
