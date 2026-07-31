/**
 * Lucide glyph, inlined as a real <svg> so it inherits the current text color.
 */
export interface IconProps {
  /** Lucide slug, e.g. "plus", "receipt", "users", "arrow-left". Must be one of the vendored glyphs in icon-paths.js. */
  name: string;
  /** Square px size. 20 in list rows, 24 in app bars, 28 in tab bars. */
  size?: number;
  /** Any CSS color; defaults to currentColor. */
  color?: string;
  /** Stroke width; the Lucide default of 2 is the brand weight. */
  strokeWidth?: number;
  style?: React.CSSProperties;
}
export function Icon(props: IconProps): JSX.Element;
