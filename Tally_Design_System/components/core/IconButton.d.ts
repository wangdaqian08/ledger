/** Circular icon-only tap target. Never rendered below 40px. */
export interface IconButtonProps {
  /** Lucide slug. */
  name: string;
  /** Diameter in px. 48 default; 40 for dense rows; 56 for a floating action. */
  size?: number;
  tone?: 'ink' | 'action' | 'mint' | 'coral' | 'lemon';
  /** plain = bare glyph · surface = white slab · tint = pale action wash · solid = filled slab */
  variant?: 'plain' | 'surface' | 'tint' | 'solid';
  /** Accessible label; falls back to the icon name. */
  label?: string;
  onClick?: (e: React.MouseEvent) => void;
  style?: React.CSSProperties;
}
export function IconButton(props: IconButtonProps): JSX.Element;
