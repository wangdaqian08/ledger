/**
 * Tally's pressable slab button.
 * @startingPoint section="Core" subtitle="Slab buttons, all tones and variants" viewport="700x300"
 */
export interface ButtonProps {
  children?: React.ReactNode;
  /** primary = colored fill + ink edge (default). solid = colored fill + self-tone edge. outline / soft / ghost are quieter. */
  variant?: 'primary' | 'solid' | 'outline' | 'soft' | 'ghost';
  /** Which brand or semantic color fills the slab. */
  tone?: 'action' | 'mint' | 'coral' | 'lemon' | 'ink';
  /** sm 40px · md 48px (default, the mobile minimum) · lg 56px */
  size?: 'sm' | 'md' | 'lg';
  /** Lucide slug rendered before the label. */
  icon?: string;
  /** Lucide slug rendered after the label. */
  iconRight?: string;
  /** Stretch to full container width — the default for sheet CTAs. */
  block?: boolean;
  disabled?: boolean;
  onClick?: (e: React.MouseEvent) => void;
  style?: React.CSSProperties;
}
export function Button(props: ButtonProps): JSX.Element;
